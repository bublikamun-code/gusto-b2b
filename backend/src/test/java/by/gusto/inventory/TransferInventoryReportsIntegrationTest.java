package by.gusto.inventory;

import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.repository.StockBalanceRepository;
import by.gusto.inventory.repository.StockLocationRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S18.3: перемещение, инвентаризация, отчёты склада.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransferInventoryReportsIntegrationTest {

    private static final UUID MAIN_LOCATION = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

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
    private StockLocationRepository locationRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID secondLocation;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        // второй склад для перемещения
        secondLocation = locationRepository.save(by.gusto.inventory.entity.StockLocation.builder()
                .name("Склад-тест-" + UUID.randomUUID())
                .active(true)
                .build()).getId();
        resetBalance("farsh-govyazhiy", MAIN_LOCATION, "40.000");
        resetBalance("farsh-govyazhiy", secondLocation, "0.000");
        resetBalance("bedro-kurinoye", MAIN_LOCATION, "40.000");
    }

    private void resetBalance(String sku, UUID location, String quantity) {
        UUID productId = productId(sku);
        StockBalance balance = balanceRepository
                .findById(new StockBalance.StockBalanceId(productId, location))
                .orElseGet(() -> balanceRepository.save(StockBalance.builder()
                        .productId(productId)
                        .locationId(location)
                        .quantity(BigDecimal.ZERO)
                        .reserved(BigDecimal.ZERO)
                        .updatedAt(Instant.now())
                        .build()));
        balance.setQuantity(new BigDecimal(quantity));
        balance.setReserved(BigDecimal.ZERO);
        balance.setUpdatedAt(Instant.now());
        balanceRepository.save(balance);
    }

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

    @SuppressWarnings("unchecked")
    private String loginAsAdmin() {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@gustomeat.by", "password", "change-me"), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse> request(String token, HttpMethod method, String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private BigDecimal available(String sku, UUID location) {
        return balanceRepository.findById(new StockBalance.StockBalanceId(productId(sku), location))
                .orElseThrow().available();
    }

    @Test
    @SuppressWarnings("unchecked")
    void transferMovesStockAndFailsWithoutEnough() {
        String token = loginAsAdmin();
        Map<String, Object> body = Map.of(
                "type", "TRANSFER",
                "locationFromId", MAIN_LOCATION.toString(),
                "locationToId", secondLocation.toString(),
                "items", List.of(Map.of("productId", productId("farsh-govyazhiy").toString(), "quantity", 15.0)));
        ResponseEntity<ApiResponse> created = request(token, HttpMethod.POST, "/api/v1/warehouse/documents", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> doc = (Map<String, Object>) created.getBody().getData();
        assertThat((String) doc.get("number")).startsWith("ПР-");

        ResponseEntity<ApiResponse> confirmed = request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents/" + doc.get("id") + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(available("farsh-govyazhiy", MAIN_LOCATION)).isEqualByComparingTo("25.000");
        assertThat(available("farsh-govyazhiy", secondLocation)).isEqualByComparingTo("15.000");

        // нехватка на источнике: весь документ откатывается, на приёмнике ничего не появляется
        Map<String, Object> big = Map.of(
                "type", "TRANSFER",
                "locationFromId", MAIN_LOCATION.toString(),
                "locationToId", secondLocation.toString(),
                "items", List.of(Map.of("productId", productId("farsh-govyazhiy").toString(), "quantity", 99.0)));
        String bigId = ((Map<String, Object>) request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents", big).getBody().getData()).get("id").toString();
        ResponseEntity<ApiResponse> rejected = request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents/" + bigId + "/confirm", Map.of());
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().getError().getCode()).isEqualTo("STOCK_INSUFFICIENT");
        assertThat(available("farsh-govyazhiy", MAIN_LOCATION)).isEqualByComparingTo("25.000");
        assertThat(available("farsh-govyazhiy", secondLocation)).isEqualByComparingTo("15.000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inventoryAdjustsToCountedFacts() {
        String token = loginAsAdmin();
        // факт 50 при учёте 40 → +10; факт 30 при учёте 40 → −10
        Map<String, Object> body = Map.of(
                "type", "INVENTORY",
                "locationFromId", MAIN_LOCATION.toString(),
                "items", List.of(
                        Map.of("productId", productId("farsh-govyazhiy").toString(), "quantity", 50.0),
                        Map.of("productId", productId("bedro-kurinoye").toString(), "quantity", 30.0)));
        ResponseEntity<ApiResponse> created = request(token, HttpMethod.POST, "/api/v1/warehouse/documents", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> doc = (Map<String, Object>) created.getBody().getData();
        assertThat((String) doc.get("number")).startsWith("ИН-");

        ResponseEntity<ApiResponse> confirmed = request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents/" + doc.get("id") + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(available("farsh-govyazhiy", MAIN_LOCATION)).isEqualByComparingTo("50.000");
        assertThat(available("bedro-kurinoye", MAIN_LOCATION)).isEqualByComparingTo("30.000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsBalanceTurnoverMovementsToOrder() {
        String token = loginAsAdmin();

        // движение для отчётов: приход +100
        Map<String, Object> incoming = Map.of(
                "type", "INCOMING",
                "locationToId", MAIN_LOCATION.toString(),
                "items", List.of(Map.of("productId", productId("farsh-govyazhiy").toString(), "quantity", 100.0)));
        String docId = ((Map<String, Object>) request(token, HttpMethod.POST,
                "/api/v1/warehouse/documents", incoming).getBody().getData()).get("id").toString();
        request(token, HttpMethod.POST, "/api/v1/warehouse/documents/" + docId + "/confirm", Map.of());

        Instant to = Instant.now().plus(1, ChronoUnit.MINUTES);
        Instant from = to.minus(1, ChronoUnit.DAYS);

        ResponseEntity<ApiResponse> balance = request(token, HttpMethod.GET,
                "/api/v1/warehouse/reports/balance?search=фарш", Map.of());
        List<Map<String, Object>> balanceRows = (List<Map<String, Object>>) balance.getBody().getData();
        assertThat(balanceRows).isNotEmpty();
        assertThat(balanceRows).allMatch(r -> ((String) r.get("productName")).toLowerCase().contains("фарш"));

        // фильтр по складу: transfer-тест мог добавить приход на второй склад
        ResponseEntity<ApiResponse> turnover = request(token, HttpMethod.GET,
                "/api/v1/warehouse/reports/turnover?locationId=" + MAIN_LOCATION
                        + "&from=" + from + "&to=" + to, Map.of());
        List<Map<String, Object>> turnoverRows = (List<Map<String, Object>>) turnover.getBody().getData();
        Map<String, Object> farsh = turnoverRows.stream()
                .filter(r -> "farsh-govyazhiy".equals(r.get("sku")))
                .findFirst().orElseThrow();
        assertThat(((Number) farsh.get("incoming")).doubleValue()).isEqualTo(150.0); // seed 50 + документ 100

        ResponseEntity<ApiResponse> movements = request(token, HttpMethod.GET,
                "/api/v1/warehouse/reports/movements?from=" + from + "&to=" + to + "&limit=500", Map.of());
        List<Map<String, Object>> movementRows = (List<Map<String, Object>>) movements.getBody().getData();
        assertThat(movementRows).isNotEmpty();
        assertThat(movementRows.get(0)).containsKeys("type", "quantity", "locationName");

        // «что заказать»: у бедра min_stock=0 в сиде — поднимем и проверим, что товар попадает в отчёт
        Product bedro = productRepository.findBySkuAndDeletedAtIsNull("bedro-kurinoye").orElseThrow();
        bedro.setMinStock(new BigDecimal("100.000"));
        productRepository.save(bedro);
        ResponseEntity<ApiResponse> toOrder = request(token, HttpMethod.GET,
                "/api/v1/warehouse/reports/to-order", Map.of());
        List<Map<String, Object>> toOrderRows = (List<Map<String, Object>>) toOrder.getBody().getData();
        assertThat(toOrderRows).anyMatch(r -> "bedro-kurinoye".equals(r.get("sku")));
        assertThat(((Number) toOrderRows.stream()
                .filter(r -> "bedro-kurinoye".equals(r.get("sku"))).findFirst().orElseThrow()
                .get("minStock")).doubleValue()).isEqualTo(100.0);
    }
}
