package by.gusto.inventory;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.repository.StockBalanceRepository;
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
 * S18.2: поставщики и заказы поставщикам.
 * Цикл приёмки: заказал 100 → принял 60 → принял 40 → RECEIVED.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SupplierPurchaseOrderIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        UUID productId = productId();
        balanceRepository.findById(new StockBalance.StockBalanceId(productId, LOCATION)).ifPresent(balance -> {
            balance.setQuantity(new BigDecimal("40.000"));
            balance.setReserved(BigDecimal.ZERO);
            balanceRepository.save(balance);
        });
    }

    private UUID productId() {
        return productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay").orElseThrow().getId();
    }

    @SuppressWarnings("unchecked")
    private String loginAsAdmin() {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@gustomeat.by", "password", "change-me"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse> request(String token, HttpMethod method, String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createSupplier(String token, String name) {
        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/warehouse/suppliers",
                Map.of("name", name, "phone", "+375 29 000-00-00", "contactPerson", "Иван"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody().getData();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createPurchaseOrder(String token, Object supplierId, double quantity) {
        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/warehouse/purchase-orders",
                Map.of("supplierId", supplierId,
                        "items", List.of(Map.of(
                                "productId", productId().toString(),
                                "quantity", quantity,
                                "purchasePrice", 20.0))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody().getData();
    }

    @SuppressWarnings("unchecked")
    private void receive(String token, Object purchaseOrderId, double quantity) {
        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/warehouse/documents",
                Map.of("type", "INCOMING",
                        "locationToId", LOCATION.toString(),
                        "purchaseOrderId", purchaseOrderId,
                        "items", List.of(Map.of("productId", productId().toString(), "quantity", quantity))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String documentId = ((Map<String, Object>) response.getBody().getData()).get("id").toString();
        ResponseEntity<ApiResponse> confirmed = request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents/" + documentId + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private BigDecimal balanceQuantity() {
        return balanceRepository.findById(new StockBalance.StockBalanceId(productId(), LOCATION))
                .orElseThrow().getQuantity();
    }

    @Test
    @SuppressWarnings("unchecked")
    void supplierCrud() {
        String token = loginAsAdmin();
        Map<String, Object> supplier = createSupplier(token, "Мясокомбинат Минский");

        assertThat((String) supplier.get("name")).isEqualTo("Мясокомбинат Минский");
        assertThat((Boolean) supplier.get("isActive")).isTrue();

        ResponseEntity<ApiResponse> updated = request(token, HttpMethod.PUT,
                "/api/v1/warehouse/suppliers/" + supplier.get("id"),
                Map.of("name", "Мясокомбинат Минский", "unp", "191536521", "contactPerson", "Пётр"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) ((Map<String, Object>) updated.getBody().getData()).get("unp")).isEqualTo("191536521");

        ResponseEntity<ApiResponse> found = request(token, HttpMethod.GET,
                "/api/v1/warehouse/suppliers?search=минск", Map.of());
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) found.getBody().getData()).isNotEmpty();

        // Регрессия S18.4: загрузка списка без search (null-параметр) не должна падать
        ResponseEntity<ApiResponse> all = request(token, HttpMethod.GET,
                "/api/v1/warehouse/suppliers", Map.of());
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) all.getBody().getData()).isNotEmpty();

        ResponseEntity<ApiResponse> deactivated = request(token, HttpMethod.DELETE,
                "/api/v1/warehouse/suppliers/" + supplier.get("id"), Map.of());
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> after = request(token, HttpMethod.GET,
                "/api/v1/warehouse/suppliers/" + supplier.get("id"), Map.of());
        assertThat((Boolean) ((Map<String, Object>) after.getBody().getData()).get("isActive")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void purchaseOrderPartialReceptionCycle() {
        String token = loginAsAdmin();
        Map<String, Object> supplier = createSupplier(token, "Ферма Заречье");

        Map<String, Object> order = createPurchaseOrder(token, supplier.get("id"), 100.0);
        assertThat((String) order.get("number")).startsWith("ЗП-");
        assertThat((String) order.get("status")).isEqualTo("DRAFT");

        ResponseEntity<ApiResponse> sent = request(token, HttpMethod.POST,
                "/api/v1/warehouse/purchase-orders/" + order.get("id") + "/send", Map.of());
        assertThat((String) ((Map<String, Object>) sent.getBody().getData()).get("status")).isEqualTo("SENT");

        receive(token, order.get("id"), 60.0);
        ResponseEntity<ApiResponse> partial = request(token, HttpMethod.GET,
                "/api/v1/warehouse/purchase-orders/" + order.get("id"), Map.of());
        Map<String, Object> partialData = (Map<String, Object>) partial.getBody().getData();
        assertThat((String) partialData.get("status")).isEqualTo("PARTIAL");
        List<Map<String, Object>> items = (List<Map<String, Object>>) partialData.get("items");
        assertThat(items.get(0).get("receivedQuantity")).isEqualTo(60.0);

        receive(token, order.get("id"), 40.0);
        ResponseEntity<ApiResponse> received = request(token, HttpMethod.GET,
                "/api/v1/warehouse/purchase-orders/" + order.get("id"), Map.of());
        assertThat((String) ((Map<String, Object>) received.getBody().getData()).get("status")).isEqualTo("RECEIVED");

        // остаток вырос на принятые 100 (демо 40 → 140)
        assertThat(balanceQuantity()).isEqualByComparingTo("140.000");

        // полученный заказ нельзя отправить/отменить
        ResponseEntity<ApiResponse> resend = request(token, HttpMethod.POST,
                "/api/v1/warehouse/purchase-orders/" + order.get("id") + "/send", Map.of());
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<ApiResponse> recancel = request(token, HttpMethod.POST,
                "/api/v1/warehouse/purchase-orders/" + order.get("id") + "/cancel", Map.of());
        assertThat(recancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void overReceptionRejectedAndRolledBack() {
        String token = loginAsAdmin();
        Map<String, Object> supplier = createSupplier(token, "Птицефабрика");
        Map<String, Object> order = createPurchaseOrder(token, supplier.get("id"), 10.0);
        request(token, HttpMethod.POST, "/api/v1/warehouse/purchase-orders/" + order.get("id") + "/send", Map.of());

        ResponseEntity<ApiResponse> response = request(token, HttpMethod.POST, "/api/v1/warehouse/documents",
                Map.of("type", "INCOMING",
                        "locationToId", LOCATION.toString(),
                        "purchaseOrderId", order.get("id"),
                        "items", List.of(Map.of("productId", productId().toString(), "quantity", 11.0))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String documentId = ((Map<String, Object>) response.getBody().getData()).get("id").toString();

        ResponseEntity<ApiResponse> confirmed = request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents/" + documentId + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(confirmed.getBody().getError().getCode()).isEqualTo("STOCK_DOCUMENT_INVALID");
        // транзакция откатилась целиком: ни остатка, ни received_quantity
        assertThat(balanceQuantity()).isEqualByComparingTo("40.000");
    }
}
