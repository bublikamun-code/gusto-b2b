package by.gusto.order;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.repository.StockBalanceRepository;
import by.gusto.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S20: создание заказа — идемпотентность, резерв, снапшот цен, пул «не назначено».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderCreationIntegrationTest {

    private static final UUID LOCATION = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection("redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockBalanceRepository balanceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        resetBalance("steyk-ribay", "40.000");
        resetBalance("bedro-kurinoye", "40.000");
    }

    private void resetBalance(String sku, String quantity) {
        UUID productId = productId(sku);
        StockBalance balance = balanceRepository
                .findById(new StockBalance.StockBalanceId(productId, LOCATION))
                .orElseThrow();
        balance.setQuantity(new BigDecimal(quantity));
        balance.setReserved(BigDecimal.ZERO);
        balanceRepository.save(balance);
    }

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

    @SuppressWarnings("unchecked")
    private String registerAndLogin() {
        String email = "buyer-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Покупатель Тест"), ApiResponse.class);
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        return (String) ((Map<String, Object>) login.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse> request(String token, HttpMethod method, String path,
                                                Map<String, Object> body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private BigDecimal reserved(String sku) {
        return balanceRepository.findById(new StockBalance.StockBalanceId(productId(sku), LOCATION))
                .orElseThrow().getReserved();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retailOrderFromCartReservesStockAndGoesToUnassignedPool() {
        String token = registerAndLogin();

        // корзина: стейк 2 кг (розничная цена из V6 seed: steyk-ribay 89.90)
        restTemplate.exchange("/api/v1/cart/items/" + productId("steyk-ribay"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("quantity", 2), authHeaders(token)),
                ApiResponse.class);

        Map<String, Object> createRequest = Map.of(
                "deliveryType", "DELIVERY",
                "deliveryAddress", "Минск, ул. Тестовая 1",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33");
        ResponseEntity<ApiResponse> created = request(token, HttpMethod.POST, "/api/v1/orders",
                createRequest, "idem-" + UUID.randomUUID());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> order = (Map<String, Object>) created.getBody().getData();
        assertThat((String) order.get("number")).matches("З-\\d{4}-\\d{5}");
        assertThat(order.get("status")).isEqualTo("NEW");
        assertThat(order.get("managerId")).isNull(); // пул «не назначено» (2.7)
        assertThat(order.get("recipientName")).isEqualTo("Иван Покупатель");

        // снапшот цены: 2 × 42.50 (seed V6) = 85.00; НДС 10% включённый: 85.00 × 10/110 = 7.73
        assertThat(((Number) order.get("totalAmount")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) order.get("totalVat")).doubleValue()).isEqualTo(7.73);

        // резерв выставлен
        assertThat(reserved("steyk-ribay")).isEqualByComparingTo("2.000");

        // корзина очищена после заказа
        ResponseEntity<ApiResponse> cart = restTemplate.exchange("/api/v1/cart",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), ApiResponse.class);
        List<Map<String, Object>> cartItems = (List<Map<String, Object>>) ((Map<String, Object>) cart.getBody().getData()).get("items");
        assertThat(cartItems).isEmpty();

        // событие в outbox (класс делит одну БД — считаем дельту)
        Integer outboxAfter = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'ORDER_CREATED'", Integer.class);
        assertThat(outboxAfter).isGreaterThan(0);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void idempotencyKeyDoesNotCreateDuplicate() {
        String token = registerAndLogin();
        String key = "idem-" + UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33",
                "items", List.of(Map.of("productId", productId("bedro-kurinoye").toString(), "quantity", 1)));

        long ordersBefore = orderRepository.count();

        ResponseEntity<ApiResponse> first = request(token, HttpMethod.POST, "/api/v1/orders", body, key);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = ((Map<String, Object>) first.getBody().getData()).get("id").toString();

        ResponseEntity<ApiResponse> second = request(token, HttpMethod.POST, "/api/v1/orders", body, key);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String repeatOrderId = ((Map<String, Object>) second.getBody().getData()).get("id").toString();

        assertThat(repeatOrderId).isEqualTo(orderId);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(reserved("bedro-kurinoye")).isEqualByComparingTo("1.000");

        // тот же ключ с другим телом → конфликт
        Map<String, Object> different = Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33",
                "items", List.of(Map.of("productId", productId("bedro-kurinoye").toString(), "quantity", 5)));
        ResponseEntity<ApiResponse> conflict = request(token, HttpMethod.POST, "/api/v1/orders", different, key);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().getError().getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void insufficientStockRejectsWholeOrder() {
        String token = registerAndLogin();
        long ordersBefore = orderRepository.count();
        Map<String, Object> body = Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33",
                "items", List.of(Map.of("productId", productId("steyk-ribay").toString(), "quantity", 41)));

        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/orders", body, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getCode()).isEqualTo("STOCK_INSUFFICIENT");

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(reserved("steyk-ribay")).isEqualByComparingTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void priceSnapshotSurvivesPriceListChange() {
        String token = registerAndLogin();
        Map<String, Object> body = Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33",
                "items", List.of(Map.of("productId", productId("bedro-kurinoye").toString(), "quantity", 2)));
        ResponseEntity<ApiResponse> created = request(token, HttpMethod.POST, "/api/v1/orders", body, null);
        double totalBefore = ((Number) ((Map<String, Object>) created.getBody().getData()).get("totalAmount")).doubleValue();

        // меняем прайс: +10 р. на товар
        jdbcTemplate.update("update product_prices set price = price + 10 where product_id = ?",
                productId("bedro-kurinoye"));

        ResponseEntity<ApiResponse> again = request(token, HttpMethod.POST, "/api/v1/orders",
                Map.of(
                        "deliveryType", "PICKUP",
                        "recipientName", "Иван Покупатель",
                        "recipientPhone", "+375 29 111-22-33",
                        "items", List.of(Map.of("productId", productId("bedro-kurinoye").toString(), "quantity", 2))),
                null);
        double totalAfter = ((Number) ((Map<String, Object>) again.getBody().getData()).get("totalAmount")).doubleValue();

        assertThat(totalAfter).isEqualTo(totalBefore + 20.0); // первый заказ зафиксировал старую цену
        Map<String, Object> firstOrder = (Map<String, Object>) created.getBody().getData();
        assertThat(((Number) firstOrder.get("totalAmount")).doubleValue()).isEqualTo(totalBefore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retailRequiresRecipient() {
        String token = registerAndLogin();
        long ordersBefore = orderRepository.count();
        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/orders",
                Map.of("deliveryType", "PICKUP",
                        "items", List.of(Map.of("productId", productId("steyk-ribay").toString(), "quantity", 1))),
                null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }
}
