package by.gusto.payment;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
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
 * S28: платежи — частичная оплата → PARTIALLY_PAID, полная → PAID;
 * запрет переплаты и отмены счёта с платежами; отчёт долга по компаниям.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentIntegrationTest {

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

    private Company createCompanyWithClient(String name, String unp) {
        Company company = companyRepository.save(Company.builder()
                .name(name)
                .unp(unp)
                .legalAddress("г. Минск, ул. Тестовая, 1")
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
    private String createIssuedInvoice(String adminToken, UUID companyId) {
        Map<String, Object> order = createLegalOrder(adminToken, companyId);
        ResponseEntity<ApiResponse> created = exchange(adminToken, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", order.get("id").toString()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = ((Map<String, Object>) created.getBody().getData()).get("id").toString();
        ResponseEntity<ApiResponse> issued = exchange(adminToken, HttpMethod.POST,
                "/api/v1/invoices/" + invoiceId + "/issue", null);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        return invoiceId;
    }

    private ResponseEntity<ApiResponse> pay(String token, String invoiceId, String amount) {
        return exchange(token, HttpMethod.POST, "/api/v1/invoices/" + invoiceId + "/payments",
                Map.of("amount", new BigDecimal(amount), "method", "безнал"));
    }

    // ----- тесты ----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void partialThenFullPaymentTransitionsStatuses() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Оплата»", "192953700");
        String invoiceId = createIssuedInvoice(admin, company.getId());

        // частичная оплата: 30 из 85 → PARTIALLY_PAID
        ResponseEntity<ApiResponse> partial = pay(admin, invoiceId, "30.00");
        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> afterPartial = exchange(admin, HttpMethod.GET,
                "/api/v1/invoices/" + invoiceId, null);
        assertThat(((Map<String, Object>) afterPartial.getBody().getData()).get("status"))
                .isEqualTo("PARTIALLY_PAID");

        // оплата сверх остатка запрещена
        ResponseEntity<ApiResponse> overpay = pay(admin, invoiceId, "100.00");
        assertThat(overpay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiResponse> negative = pay(admin, invoiceId, "-5.00");
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // доплата остатка: 55 → PAID
        ResponseEntity<ApiResponse> rest = pay(admin, invoiceId, "55.00");
        assertThat(rest.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> afterFull = exchange(admin, HttpMethod.GET,
                "/api/v1/invoices/" + invoiceId, null);
        assertThat(((Map<String, Object>) afterFull.getBody().getData()).get("status")).isEqualTo("PAID");

        // оплата оплаченного счёта → 409
        ResponseEntity<ApiResponse> extra = pay(admin, invoiceId, "1.00");
        assertThat(extra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(extra.getBody().getError().getCode()).isEqualTo("INVOICE_INVALID_STATE");

        // история платежей
        ResponseEntity<ApiResponse> history = exchange(admin, HttpMethod.GET,
                "/api/v1/invoices/" + invoiceId + "/payments", null);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) history.getBody().getData()).hasSize(2);

        // аудит платежа
        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'PAYMENT_REGISTER' and target_id = ?",
                Integer.class, UUID.fromString(invoiceId));
        assertThat(auditRows).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelWithPaymentsForbidden() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Отмена»", "192953701");
        String invoiceId = createIssuedInvoice(admin, company.getId());

        pay(admin, invoiceId, "10.00");

        ResponseEntity<ApiResponse> cancelled = exchange(admin, HttpMethod.POST,
                "/api/v1/invoices/" + invoiceId + "/cancel", null);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancelled.getBody().getError().getCode()).isEqualTo("INVOICE_INVALID_STATE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void debtReportAggregatesPerCompany() {
        String admin = login("admin@gustomeat.by", "change-me");

        // компания A: счёт 85, оплатила 30 → долг 55
        Company a = createCompanyWithClient("ООО «ДолжникА»", "192953710");
        String invoiceA = createIssuedInvoice(admin, a.getId());
        pay(admin, invoiceA, "30.00");

        // компания B: полностью оплатила → не попадает в отчёт
        Company b = createCompanyWithClient("ООО «Честная»", "192953711");
        String invoiceB = createIssuedInvoice(admin, b.getId());
        pay(admin, invoiceB, "85.00");

        ResponseEntity<ApiResponse> report = exchange(admin, HttpMethod.GET,
                "/api/v1/invoices/debts", null);
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.getBody().getData();

        Map<String, Object> rowA = rows.stream()
                .filter(r -> "ООО «ДолжникА»".equals(r.get("companyName")))
                .findFirst().orElseThrow();
        assertThat(((Number) rowA.get("invoiced")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) rowA.get("paid")).doubleValue()).isEqualTo(30.00);
        assertThat(((Number) rowA.get("debt")).doubleValue()).isEqualTo(55.00);

        // оплаченный счёт компании B не создаёт строку долга
        assertThat(rows).noneMatch(r -> "ООО «Честная»".equals(r.get("companyName")));

        // бухгалтер имеет доступ к отчёту
        String accEmail = "acc-" + UUID.randomUUID() + "@test.by";
        userRepository.save(User.builder()
                .email(accEmail)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Бухгалтер")
                .role(Role.ACCOUNTANT)
                .active(true)
                .build());
        String accountant = login(accEmail, "staff-pass");
        assertThat(exchange(accountant, HttpMethod.GET, "/api/v1/invoices/debts", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
