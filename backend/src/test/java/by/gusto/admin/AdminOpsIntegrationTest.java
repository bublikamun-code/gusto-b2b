package by.gusto.admin;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S38: операционный центр — настройки из админки (реквизиты отражаются в новых
 * документах), журнал аудита с фильтрами, дашборд процессов, транспорт не редактируется.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminOpsIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String login(Role role) {
        String email = "staff-" + UUID.randomUUID() + "@test.by";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник " + role)
                .role(role)
                .active(true)
                .build());
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "staff-pass"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsEditableAndReflectedInNewInvoices() {
        String admin = login(Role.ADMIN);

        // читаем настройки
        ResponseEntity<ApiResponse> settings = restTemplate.exchange(
                "/api/v1/admin/ops/settings", HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(settings.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) settings.getBody().getData();
        assertThat(items).extracting(s -> s.get("key"))
                .contains("seller.requisites", "vat.default", "auth.require_email_confirmation");

        // правим реквизиты
        String newName = "ЧТУП «Густо Опт»";
        ResponseEntity<ApiResponse> updated = restTemplate.exchange(
                "/api/v1/admin/ops/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "key", "seller.requisites",
                        "value", "{\"name\": \"" + newName + "\", \"unp\": \"191536521\"}"),
                        authHeaders(admin)), ApiResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // транспорт из .env не редактируется
        ResponseEntity<ApiResponse> forbidden = restTemplate.exchange(
                "/api/v1/admin/ops/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("key", "smtp.password", "value", "\"x\""),
                        authHeaders(admin)), ApiResponse.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // смена реквизитов отражается в НОВЫХ счетах (приёмка S38)
        String adminMain = loginAdmin();
        UUID company = createCompany("ООО «Реквизиты»");
        String orderId = createOrder(adminMain, company);
        String invoiceId = createInvoice(adminMain, orderId);
        ResponseEntity<ApiResponse> invoice = restTemplate.exchange(
                "/api/v1/invoices/" + invoiceId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminMain)), ApiResponse.class);
        Map<String, Object> seller = (Map<String, Object>) ((Map<String, Object>) invoice.getBody().getData())
                .get("sellerSnapshot");
        assertThat(seller.get("name")).isEqualTo(newName);
    }

    @Test
    @SuppressWarnings("unchecked")
    void auditFilteringAndProcessDashboard() {
        String admin = loginAdmin();

        // генерируем действия: заказ (ORDER_CREATE не пишется, но фильтр по targetType работает)
        UUID company = createCompany("ООО «Аудит»");
        String orderId = createOrder(admin, company);
        createInvoice(admin, orderId);

        ResponseEntity<ApiResponse> audit = restTemplate.exchange(
                "/api/v1/admin/ops/audit?targetType=invoice", HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) audit.getBody().getData();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> "invoice".equals(r.get("target_type")));

        // дашборд процессов: ключи на месте, типы корректны
        ResponseEntity<ApiResponse> dashboard = restTemplate.exchange(
                "/api/v1/admin/ops/dashboard", HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = (Map<String, Object>) dashboard.getBody().getData();
        assertThat(d).containsKeys("ordersToday", "requestsToday", "unassignedOrders",
                "belowMinStock", "unpaidInvoices", "unpaidAmount", "overdueTasks");

        // публичный лендинг из настроек (правится без коммита)
        restTemplate.exchange("/api/v1/admin/ops/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("key", "landing.hero",
                        "value", "{\"title\": \"Мясо с утра\", \"subtitle\": \"ГУСТО\"}"),
                        authHeaders(admin)), ApiResponse.class);
        ResponseEntity<String> landing =
                restTemplate.getForEntity("/api/v1/cms/landing", String.class);
        assertThat(landing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(landing.getBody()).contains("Мясо с утра");
    }

    // ----- helpers --------------------------------------------------------------

    private String loginAdmin() {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@gustomeat.by", "password", "change-me"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private UUID createCompany(String name) {
        return jdbcTemplate.queryForObject(
                "insert into companies (name, unp) values (?, '192953799') returning id",
                (rs, n) -> UUID.fromString(rs.getString("id")),
                name);
    }

    @SuppressWarnings("unchecked")
    private String createOrder(String admin, UUID companyId) {
        UUID productId = jdbcTemplate.queryForObject(
                "select id from products where sku = 'steyk-ribay'", UUID.class);
        ResponseEntity<ApiResponse> created = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "customerCompanyId", companyId.toString(),
                        "deliveryType", "PICKUP",
                        "items", List.of(Map.of("productId", productId.toString(), "quantity", 2))),
                        authHeaders(admin)), ApiResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Map<String, Object>) created.getBody().getData()).get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String createInvoice(String admin, String orderId) {
        ResponseEntity<ApiResponse> created = restTemplate.exchange("/api/v1/invoices", HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", orderId), authHeaders(admin)), ApiResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = ((Map<String, Object>) created.getBody().getData()).get("id").toString();
        ResponseEntity<ApiResponse> issued = restTemplate.exchange(
                "/api/v1/invoices/" + invoiceId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        return invoiceId;
    }
}
