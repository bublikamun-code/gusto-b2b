package by.gusto.waybill;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * S26: ТН/ТТН — создание из заказа со снапшотами и массой позиций,
 * нумерация с серией, PDF сразу при создании, права, кабинет юрлица.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WaybillIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        StockBalance balance = balanceRepository
                .findById(new StockBalance.StockBalanceId(productId("steyk-ribay"), LOCATION))
                .orElseThrow();
        balance.setQuantity(new BigDecimal("40.000"));
        balance.setReserved(BigDecimal.ZERO);
        balanceRepository.save(balance);
    }

    // ----- helpers --------------------------------------------------------------

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse> exchange(String token, HttpMethod method, String path, Object body) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), ApiResponse.class);
    }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private String createStaffAndLogin(Role role, String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник " + role)
                .role(role)
                .active(true)
                .build());
        return login(email, "staff-pass");
    }

    private Company createCompanyWithClient(String name, String unp) {
        Company company = companyRepository.save(Company.builder()
                .name(name)
                .unp(unp)
                .legalAddress("г. Минск, ул. Монтажников, 39-109Б")
                .build());
        userRepository.save(User.builder()
                .email("legal-" + UUID.randomUUID() + "@test.by")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Клиент Юрлицо")
                .role(Role.CUSTOMER_LEGAL)
                .companyId(company.getId())
                .active(true)
                .emailConfirmedAt(java.time.Instant.now())
                .build());
        return company;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createLegalOrder(String adminToken, UUID companyId) {
        ResponseEntity<ApiResponse> created = exchange(adminToken, HttpMethod.POST, "/api/v1/orders", Map.of(
                "customerCompanyId", companyId.toString(),
                "deliveryType", "PICKUP",
                "items", List.of(Map.of(
                        "productId", productId("steyk-ribay").toString(),
                        "quantity", 2))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) created.getBody().getData();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<ApiResponse> response) {
        return (Map<String, Object>) response.getBody().getData();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listData(ResponseEntity<ApiResponse> response) {
        return (List<Map<String, Object>>) response.getBody().getData();
    }

    // ----- тесты ----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void createTtnWithSnapshotsWeightAndPdf() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «ТиоптТрейд»", "192953686");
        Map<String, Object> order = createLegalOrder(admin, company.getId());

        ResponseEntity<ApiResponse> response = exchange(admin, HttpMethod.POST, "/api/v1/waybills", Map.of(
                "orderId", order.get("id").toString(),
                "type", "TTN",
                "vehicle", "AB 1234-5",
                "driver", "Иванов И.И."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> waybill = data(response);

        // нумерация ТТН-<серия>-<N> (2.2, seed-серия A)
        assertThat((String) waybill.get("number")).matches("ТТН-A-\\d+");
        assertThat((String) waybill.get("displayNumber")).matches("ТТН-A-\\d+ от \\d{2}\\.\\d{2}\\.\\d{4}");

        // снапшоты сторон и транспорт (пример ТиоптТрейд)
        assertThat((String) ((Map) waybill.get("sellerSnapshot")).get("name")).isEqualTo("ЧТУП «ЛорСан»");
        assertThat((String) ((Map) waybill.get("buyerSnapshot")).get("name")).isEqualTo("ООО «ТиоптТрейд»");
        assertThat((String) ((Map) waybill.get("carrierSnapshot")).get("vehicle")).isEqualTo("AB 1234-5");

        // позиции: снапшот + масса = количество × вес единицы товара
        List<Map<String, Object>> items = (List<Map<String, Object>>) waybill.get("items");
        assertThat(items).hasSize(1);
        Product product = productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay").orElseThrow();
        BigDecimal expectedWeight = product.getWeightPerUnit() == null ? null
                : new BigDecimal("2").multiply(product.getWeightPerUnit()).setScale(3, java.math.RoundingMode.HALF_UP);
        if (product.getWeightPerUnit() == null) {
            assertThat(items.get(0).get("weight")).isNull();
        } else {
            assertThat(((Number) items.get(0).get("weight")).doubleValue())
                    .isEqualTo(expectedWeight.doubleValue());
        }
        assertThat(((Number) waybill.get("totalAmount")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) waybill.get("totalVat")).doubleValue()).isEqualTo(7.73);

        // PDF создан сразу и доступен
        UUID waybillId = UUID.fromString(waybill.get("id").toString());
        assertThat(jdbcTemplate.queryForObject(
                "select pdf_file_id is not null from waybills where id = ?", Boolean.class, waybillId)).isTrue();
        ResponseEntity<byte[]> pdf = downloadPdf(admin, "/api/v1/waybills/" + waybillId + "/pdf");
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // аудит
        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where target_type = 'waybill' and action = 'WAYBILL_CREATE'",
                Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tnAndTtnHaveIndependentNumbering() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Номера»", "192953690");
        Map<String, Object> order = createLegalOrder(admin, company.getId());

        ResponseEntity<ApiResponse> tn = exchange(admin, HttpMethod.POST, "/api/v1/waybills", Map.of(
                "orderId", order.get("id").toString(),
                "type", "TN"));
        ResponseEntity<ApiResponse> ttn = exchange(admin, HttpMethod.POST, "/api/v1/waybills", Map.of(
                "orderId", order.get("id").toString(),
                "type", "TTN"));
        assertThat(tn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ttn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) data(tn).get("number")).startsWith("ТН-A-");
        assertThat((String) data(ttn).get("number")).startsWith("ТТН-A-");
        // номера разные, серии общие
        assertThat(data(tn).get("number")).isNotEqualTo(data(ttn).get("number"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rightsAndCabinetAccess() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Доступ»", "192953691");
        Map<String, Object> order = createLegalOrder(admin, company.getId());

        // бухгалтер оформляет (матрица 2.1)
        String accountant = createStaffAndLogin(Role.ACCOUNTANT, "acc-wb-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> created = exchange(accountant, HttpMethod.POST, "/api/v1/waybills", Map.of(
                "orderId", order.get("id").toString(),
                "type", "TTN",
                "vehicle", "AB 7777-7",
                "driver", "Петров П.П."));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> waybill = data(created);
        UUID waybillId = UUID.fromString(waybill.get("id").toString());

        // розничному заказу накладная не оформляется
        String buyer = "shopper-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", buyer, "password", "password123", "fullName", "Розница"), ApiResponse.class);
        String retailToken = login(buyer, "password123");
        ResponseEntity<ApiResponse> retailOrder = exchange(retailToken, HttpMethod.POST, "/api/v1/orders", Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван",
                "recipientPhone", "+375 29 000-00-00",
                "items", List.of(Map.of(
                        "productId", productId("steyk-ribay").toString(),
                        "quantity", 1))));
        UUID retailOrderId = UUID.fromString(
                ((Map<String, Object>) retailOrder.getBody().getData()).get("id").toString());
        ResponseEntity<ApiResponse> retailWaybill = exchange(admin, HttpMethod.POST, "/api/v1/waybills",
                Map.of("orderId", retailOrderId.toString(), "type", "TTN"));
        assertThat(retailWaybill.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // кабинет юрлица: список и PDF своих накладных
        String legalEmail = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER_LEGAL && company.getId().equals(u.getCompanyId()))
                .map(u -> u.getEmail()).findFirst().orElseThrow();
        String legalToken = login(legalEmail, "password123");
        ResponseEntity<ApiResponse> cabinetList = exchange(legalToken, HttpMethod.GET,
                "/api/v1/cabinet/waybills", null);
        assertThat(cabinetList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listData(cabinetList)).extracting(w -> w.get("id")).contains(waybill.get("id"));

        ResponseEntity<byte[]> cabinetPdf = downloadPdf(legalToken, "/api/v1/cabinet/waybills/" + waybillId + "/pdf");
        assertThat(cabinetPdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(cabinetPdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");

        // чужой менеджер не видит накладную в списке
        String outsider = createStaffAndLogin(Role.MANAGER, "mgr-wb-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> managerList = exchange(outsider, HttpMethod.GET, "/api/v1/waybills", null);
        assertThat(listData(managerList)).extracting(w -> w.get("id")).doesNotContain(waybill.get("id"));
    }

    private ResponseEntity<byte[]> downloadPdf(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }
}
