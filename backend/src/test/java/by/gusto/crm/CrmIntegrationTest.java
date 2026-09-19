package by.gusto.crm;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S29: CRM — воронка лидов с валидацией переходов, назначение менеджеру,
 * задачи и заметки, дашборд руководителя (выручка, топы, долг, конверсия).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CrmIntegrationTest {

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

    private UUID staffId(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
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
    void leadFunnelWithAssignmentAndInvalidTransitions() {
        String admin = login("admin@gustomeat.by", "change-me");
        String mgrEmail = "mgr-crm-" + UUID.randomUUID() + "@test.by";
        String manager = createStaffAndLogin(Role.MANAGER, mgrEmail);
        UUID managerId = staffId(mgrEmail);

        // создание лида → NEW, в пуле
        ResponseEntity<ApiResponse> created = exchange(admin, HttpMethod.POST, "/api/v1/crm/leads", Map.of(
                "name", "Заявка с сайта",
                "phone", "+375 29 555-11-22",
                "companyName", "ООО «Лид»",
                "message", "Нужны поставки мяса",
                "source", "website"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> lead = data(created);
        assertThat(lead.get("status")).isEqualTo("NEW");
        String leadId = lead.get("id").toString();

        // пропуск стадий → 409
        ResponseEntity<ApiResponse> skip = exchange(admin, HttpMethod.POST,
                "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", "WON"));
        assertThat(skip.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(skip.getBody().getError().getCode()).isEqualTo("LEAD_STATUS_TRANSITION");

        // назначение менеджеру, затем воронка NEW→IN_PROGRESS→QUALIFIED→WON
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads/" + leadId + "/assign",
                Map.of("managerId", managerId.toString()));
        for (String status : List.of("IN_PROGRESS", "QUALIFIED", "WON")) {
            ResponseEntity<ApiResponse> step = exchange(manager, HttpMethod.POST,
                    "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", status));
            assertThat(step.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(step).get("status")).isEqualTo(status);
        }

        // переход из терминального → 409
        ResponseEntity<ApiResponse> terminal = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", "LOST"));
        assertThat(terminal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // фильтры: won показывает лид, unassigned пуст
        ResponseEntity<ApiResponse> won = exchange(admin, HttpMethod.GET,
                "/api/v1/crm/leads?scope=all&status=WON", null);
        assertThat(listData(won))
                .extracting(l -> l.get("id"))
                .contains(leadId);

        // чужой менеджер не может менять лид (не назначен и не в пуле)
        String otherEmail = "mgr-other-" + UUID.randomUUID() + "@test.by";
        createStaffAndLogin(Role.MANAGER, otherEmail);
        UUID outsiderId = staffId(otherEmail);
        ResponseEntity<ApiResponse> created2 = exchange(admin, HttpMethod.POST, "/api/v1/crm/leads", Map.of(
                "name", "Лид другого менеджера"));
        String lead2 = data(created2).get("id").toString();
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads/" + lead2 + "/assign",
                Map.of("managerId", outsiderId.toString()));
        ResponseEntity<ApiResponse> alien = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/leads/" + lead2 + "/status", Map.of("status", "IN_PROGRESS"));
        assertThat(alien.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // бухгалтеру CRM недоступна (матрица 2.1)
        String accountant = createStaffAndLogin(Role.ACCOUNTANT, "acc-crm-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> byAcc = exchange(accountant, HttpMethod.GET,
                "/api/v1/crm/leads", null);
        assertThat(byAcc.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tasksAndNotesLifecycle() {
        String mgrEmail = "mgr-task-" + UUID.randomUUID() + "@test.by";
        String manager = createStaffAndLogin(Role.MANAGER, mgrEmail);
        UUID managerId = staffId(mgrEmail);

        // задача с просроченным сроком → overdue
        ResponseEntity<ApiResponse> created = exchange(manager, HttpMethod.POST, "/api/v1/crm/tasks", Map.of(
                "title", "Позвонить клиенту",
                "dueDate", Instant.now().minusSeconds(3600).toString()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String taskId = data(created).get("id").toString();

        ResponseEntity<ApiResponse> overdue = exchange(manager, HttpMethod.GET,
                "/api/v1/crm/tasks?scope=overdue", null);
        assertThat(listData(overdue))
                .extracting(t -> t.get("id"))
                .contains(taskId);
        assertThat(listData(overdue))
                .extracting(t -> t.get("overdue"))
                .containsOnly(true);

        // закрытие; повторное закрытие → 409
        exchange(manager, HttpMethod.POST, "/api/v1/crm/tasks/" + taskId + "/status?status=DONE", null);
        ResponseEntity<ApiResponse> again = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/tasks/" + taskId + "/status?status=CANCELLED", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // заметка по компании
        Company company = companyRepository.save(Company.builder()
                .name("ООО «Заметки»").unp("192953720").build());
        ResponseEntity<ApiResponse> note = exchange(manager, HttpMethod.POST, "/api/v1/crm/notes", Map.of(
                "companyId", company.getId().toString(),
                "body", "Договорились о пробной поставке"));
        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> notes = exchange(manager, HttpMethod.GET,
                "/api/v1/crm/notes?companyId=" + company.getId(), null);
        assertThat(listData(notes).get(0).get("body"))
                .isEqualTo("Договорились о пробной поставке");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardAggregatesRevenueTopsDebtAndConversion() {
        String admin = login("admin@gustomeat.by", "change-me");

        // completed-заказ юрлица → выручка и топ-клиент
        Company company = companyRepository.save(Company.builder()
                .name("ООО «Дашборд»").unp("192953721").build());
        ResponseEntity<ApiResponse> created = exchange(admin, HttpMethod.POST, "/api/v1/orders", Map.of(
                "customerCompanyId", company.getId().toString(),
                "deliveryType", "PICKUP",
                "items", List.of(Map.of(
                        "productId", productId("steyk-ribay").toString(),
                        "quantity", 2))));
        String orderId = data(created).get("id").toString();
        jdbcTemplate.update("update orders set status = 'COMPLETED' where id = ?", UUID.fromString(orderId));

        // лид в WON для конверсии
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads", Map.of("name", "Конверсия"));
        ResponseEntity<ApiResponse> lead = exchange(admin, HttpMethod.GET,
                "/api/v1/crm/leads?scope=all&status=NEW", null);
        String leadId = listData(lead).get(0).get("id").toString();
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", "IN_PROGRESS"));
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", "QUALIFIED"));
        exchange(admin, HttpMethod.POST, "/api/v1/crm/leads/" + leadId + "/status", Map.of("status", "WON"));

        ResponseEntity<ApiResponse> dashboard = exchange(admin, HttpMethod.GET,
                "/api/v1/crm/dashboard", null);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = data(dashboard);
        assertThat(((Number) d.get("revenue")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) d.get("completedOrders")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat((List<?>) d.get("topProducts")).isNotEmpty();
        assertThat((List<?>) d.get("topCustomers")).isNotEmpty();
        assertThat(((Number) d.get("leadsWon")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) d.get("leadConversionPercent")).doubleValue()).isGreaterThan(0);
    }
}
