package by.gusto.inventory;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.entity.WarehouseDocument;
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
 * S18.1: складские документы приход/расход/списание.
 * Подтверждение — единственная точка создания движений.
 * Демо-данные: steyk-na-kosti, остаток 25 на складе по умолчанию (V9).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WarehouseDocumentIntegrationTest {

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
        // тесты класса независимы: возвращаем демо-остаток 25 перед каждым
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-na-kosti").orElseThrow().getId();
        balanceRepository.findById(new StockBalance.StockBalanceId(productId, LOCATION)).ifPresent(balance -> {
            balance.setQuantity(new BigDecimal("25.000"));
            balance.setReserved(BigDecimal.ZERO);
            balanceRepository.save(balance);
        });
    }

    @SuppressWarnings("unchecked")
    private String loginAsAdmin() {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@gustomeat.by", "password", "change-me"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private String registerAndLoginIndividual() {
        String email = "shopper-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Розница Тест"), ApiResponse.class);
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse> postJson(String token, String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createDocument(String token, String type, String locationField, BigDecimal quantity) {
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-na-kosti").orElseThrow().getId();
        Map<String, Object> body = Map.of(
                "type", type,
                locationField, LOCATION.toString(),
                "items", List.of(Map.of(
                        "productId", productId.toString(),
                        "quantity", quantity,
                        "price", 25.50)));
        ResponseEntity<ApiResponse> response = postJson(token, "/api/v1/warehouse/documents", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody().getData();
    }

    private BigDecimal balanceQuantity() {
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-na-kosti").orElseThrow().getId();
        return balanceRepository.findById(new StockBalance.StockBalanceId(productId, LOCATION))
                .orElseThrow().getQuantity();
    }

    @Test
    void incomingOutgoingWriteOffCycle() {
        String token = loginAsAdmin();

        Map<String, Object> incoming = createDocument(token, "INCOMING", "locationToId", new BigDecimal("100.000"));
        assertThat((String) incoming.get("number")).startsWith("ПН-");
        assertThat((String) incoming.get("status")).isEqualTo("DRAFT");
        // черновик не создаёт движений
        assertThat(balanceQuantity()).isEqualByComparingTo("25.000");

        ResponseEntity<ApiResponse> confirmed = postJson(token,
                "/api/v1/warehouse/documents/" + incoming.get("id") + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) confirmed.getBody().getData()).get("status")).isEqualTo("CONFIRMED");
        assertThat(balanceQuantity()).isEqualByComparingTo("125.000");

        Map<String, Object> outgoing = createDocument(token, "OUTGOING", "locationFromId", new BigDecimal("30.000"));
        assertThat((String) outgoing.get("number")).startsWith("РС-");
        postJson(token, "/api/v1/warehouse/documents/" + outgoing.get("id") + "/confirm", Map.of());
        assertThat(balanceQuantity()).isEqualByComparingTo("95.000");

        Map<String, Object> writeOff = createDocument(token, "WRITE_OFF", "locationFromId", new BigDecimal("10.000"));
        assertThat((String) writeOff.get("number")).startsWith("СП-");
        postJson(token, "/api/v1/warehouse/documents/" + writeOff.get("id") + "/confirm", Map.of());
        assertThat(balanceQuantity()).isEqualByComparingTo("85.000");
    }

    @Test
    void draftCanBeCancelledButConfirmedCannot() {
        String token = loginAsAdmin();

        Map<String, Object> draft = createDocument(token, "INCOMING", "locationToId", new BigDecimal("5.000"));
        ResponseEntity<ApiResponse> cancelled = postJson(token,
                "/api/v1/warehouse/documents/" + draft.get("id") + "/cancel", Map.of());
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) cancelled.getBody().getData()).get("status")).isEqualTo("CANCELLED");
        assertThat(balanceQuantity()).isEqualByComparingTo("25.000"); // движения не было

        Map<String, Object> doc = createDocument(token, "INCOMING", "locationToId", new BigDecimal("7.000"));
        postJson(token, "/api/v1/warehouse/documents/" + doc.get("id") + "/confirm", Map.of());
        assertThat(balanceQuantity()).isEqualByComparingTo("32.000");

        // повторное подтверждение и отмена подтверждённого — запрещены
        ResponseEntity<ApiResponse> reconfirm = postJson(token,
                "/api/v1/warehouse/documents/" + doc.get("id") + "/confirm", Map.of());
        assertThat(reconfirm.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reconfirm.getBody().getError().getCode()).isEqualTo("STOCK_DOCUMENT_INVALID");

        ResponseEntity<ApiResponse> cancelConfirmed = postJson(token,
                "/api/v1/warehouse/documents/" + doc.get("id") + "/cancel", Map.of());
        assertThat(cancelConfirmed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(balanceQuantity()).isEqualByComparingTo("32.000");
    }

    @Test
    void outgoingBeyondStockRejectedAtConfirm() {
        String token = loginAsAdmin();
        Map<String, Object> doc = createDocument(token, "OUTGOING", "locationFromId", new BigDecimal("26.000"));
        ResponseEntity<ApiResponse> confirmed = postJson(token,
                "/api/v1/warehouse/documents/" + doc.get("id") + "/confirm", Map.of());
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(confirmed.getBody().getError().getCode()).isEqualTo("STOCK_INSUFFICIENT");
        // документ остался черновиком: остаток не тронут, можно исправить позиции
        assertThat(balanceQuantity()).isEqualByComparingTo("25.000");
    }

    @Test
    void documentsAreForbiddenForCustomers() {
        String token = registerAndLoginIndividual();
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-na-kosti").orElseThrow().getId();
        Map<String, Object> body = Map.of(
                "type", "INCOMING",
                "locationToId", LOCATION.toString(),
                "items", List.of(Map.of("productId", productId.toString(), "quantity", 1)));
        ResponseEntity<ApiResponse> response = postJson(token, "/api/v1/warehouse/documents", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByType() {
        String token = loginAsAdmin();
        createDocument(token, "INCOMING", "locationToId", new BigDecimal("1.000"));
        createDocument(token, "WRITE_OFF", "locationFromId", new BigDecimal("1.000"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/warehouse/documents?type=WRITE_OFF", HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().getData();
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(d -> "WRITE_OFF".equals(d.get("type")));
    }
}
