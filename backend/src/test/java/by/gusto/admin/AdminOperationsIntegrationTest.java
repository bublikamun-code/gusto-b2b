package by.gusto.admin;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S38: операционный центр — настройки (чтение/запись групп, права, валидация,
 * приёмка «реквизиты → новые PDF»), журнал аудита с фильтрами, дашборд
 * процессов, предпросмотр импорта без записи.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminOperationsIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // тесты класса делят одну БД: возвращаем seed-настройки (V2/V8/V9),
        // которые меняют тесты этого класса
        redisTemplate.getConnectionFactory().getConnection().flushAll(); // сброс rate limit логина (S08)
        jdbcTemplate.update("update settings set value = '\"A\"' where key = 'document.series.ttn'");
        jdbcTemplate.update("update settings set value = '\"A\"' where key = 'document.series.tn'");
        jdbcTemplate.update("update settings set value = '10' where key = 'vat.default'");
        jdbcTemplate.update("update settings set value = 'false' where key = 'auth.require_email_confirmation'");
        jdbcTemplate.update("update settings set value = ?::jsonb where key = 'seller.requisites'",
                "{\"name\": \"ЧТУП «ЛорСан»\", \"unp\": \"191536521\", \"address\": "
                        + "\"220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ\", \"bank_account\": \"\", "
                        + "\"bank_name\": \"\", \"bank_bic\": \"\"}");
        jdbcTemplate.update("delete from settings where key = 'telegram.bot_token'");
        jdbcTemplate.update("delete from settings where key = 'notifications.rules'");
        jdbcTemplate.update("delete from settings where key = 'landing.hero'");
        jdbcTemplate.update("delete from settings where key = 'landing.delivery'");
        jdbcTemplate.update("update products set min_stock = 0");
        jdbcTemplate.update("delete from audit_log where action = 'SETTINGS_UPDATE'");
    }

    // ----- helpers --------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<ApiResponse> response) {
        return (Map<String, Object>) response.getBody().getData();
    }

    private ResponseEntity<ApiResponse> exchange(String token, HttpMethod method,
                                                 String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private String createStaffAndLogin(Role role) {
        String email = "staff-" + UUID.randomUUID() + "@test.by";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник " + role)
                .role(role)
                .active(true)
                .build());
        return login(email, "staff-pass");
    }

    private byte[] xlsx(List<Map<String, String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("sku");
            header.createCell(1).setCellValue("value");
            int i = 1;
            for (Map<String, String> entry : rows) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(entry.get("sku"));
                row.createCell(1).setCellValue(entry.get("value"));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<ApiResponse> preview(String token, String type, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "prices.xlsx";
            }
        });
        return restTemplate.postForEntity("/api/v1/admin/import/" + type + "/preview",
                new HttpEntity<>(body, headers), ApiResponse.class);
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
                .emailConfirmedAt(Instant.now())
                .build());
        return company;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createLegalOrder(String adminToken, UUID companyId) {
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay")
                .orElseThrow().getId();
        ResponseEntity<ApiResponse> created = exchange(adminToken, HttpMethod.POST, "/api/v1/orders",
                Map.of(
                        "customerCompanyId", companyId.toString(),
                        "deliveryType", "PICKUP",
                        "items", List.of(Map.of(
                                "productId", productId.toString(),
                                "quantity", 1))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) created.getBody().getData();
    }

    // ----- настройки ------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void settingsShowSeedValuesAndRequireAdmin() {
        String admin = login("admin@gustomeat.by", "change-me");
        ResponseEntity<ApiResponse> response = exchange(admin, HttpMethod.GET,
                "/api/v1/admin/settings", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> settings = data(response);
        Map<String, Object> seller = (Map<String, Object>) settings.get("seller");
        assertThat(seller.get("name")).isEqualTo("ЧТУП «ЛорСан»");
        Map<String, Object> documents = (Map<String, Object>) settings.get("documents");
        assertThat(documents.get("seriesTtn")).isEqualTo("A");
        assertThat(((Number) documents.get("vatDefault")).doubleValue()).isEqualTo(10.0);
        Map<String, Object> auth = (Map<String, Object>) settings.get("auth");
        assertThat(auth.get("requireEmailConfirmation")).isEqualTo(false);
        List<Map<String, Object>> locations =
                (List<Map<String, Object>>) settings.get("locations");
        assertThat(locations).isNotEmpty();

        // настройки — только ADMIN (матрица 2.1)
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);
        assertThat(exchange(accountant, HttpMethod.GET, "/api/v1/admin/settings", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(accountant, HttpMethod.PUT, "/api/v1/admin/settings/seller",
                Map.of("name", "X", "unp", "1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sellerRequisitesUpdateIsReflectedInNewInvoices() {
        String admin = login("admin@gustomeat.by", "change-me");

        ResponseEntity<ApiResponse> updated = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/seller", Map.of(
                        "name", "ООО «Густо Опт»",
                        "unp", "190111222",
                        "address", "г. Минск, пр-т Независимости, 1",
                        "bankAccount", "BY00BLBB00000000000000000000",
                        "bankName", "ЗАО «Банк»",
                        "bankBic", "BLBBBY2X"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // приёмка S38: смена реквизитов отражается в новых документах (снапшот счёта)
        Company company = createCompanyWithClient("ООО «РеквизитыТест»", "190222333");
        Map<String, Object> order = createLegalOrder(admin, company.getId());
        ResponseEntity<ApiResponse> invoice = exchange(admin, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", order.get("id").toString()));
        assertThat(invoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> invoiceData = data(invoice);
        Map<String, Object> seller = (Map<String, Object>) invoiceData.get("sellerSnapshot");
        assertThat(seller.get("name")).isEqualTo("ООО «Густо Опт»");
        assertThat(seller.get("unp")).isEqualTo("190111222");

        // возврат seed-реквизитов, чтобы не влиять на другие тесты класса
        exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/seller", Map.of(
                "name", "ЧТУП «ЛорСан»", "unp", "191536521",
                "address", "220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ"));
    }

    @Test
    void documentsValidationWritesAndAudits() {
        String admin = login("admin@gustomeat.by", "change-me");

        assertThat(exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/documents",
                Map.of("seriesTtn", "", "seriesTn", "A", "vatDefault", 10))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/documents",
                Map.of("seriesTtn", "B", "seriesTn", "A", "vatDefault", 99))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiResponse> updated = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/documents",
                Map.of("seriesTtn", "B", "seriesTn", "C", "vatDefault", 20));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> documents = (Map<String, Object>) data(updated).get("documents");
        assertThat(documents.get("seriesTtn")).isEqualTo("B");
        assertThat(documents.get("vatDefault")).isEqualTo(20.0);

        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'SETTINGS_UPDATE' "
                        + "and after->>'group' = 'documents'",
                Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stockLocationValidatedAndAuthGateUpdated() {
        String admin = login("admin@gustomeat.by", "change-me");

        assertThat(exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/stock",
                Map.of("defaultLocationId", UUID.randomUUID().toString()))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiResponse> settings = exchange(admin, HttpMethod.GET,
                "/api/v1/admin/settings", null);
        List<Map<String, Object>> locations =
                (List<Map<String, Object>>) data(settings).get("locations");
        String locationId = locations.get(0).get("id").toString();
        ResponseEntity<ApiResponse> updated = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/stock", Map.of("defaultLocationId", locationId));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) data(updated).get("stock")).get("defaultLocationId"))
                .isEqualTo(locationId);

        ResponseEntity<ApiResponse> auth = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/auth", Map.of("requireEmailConfirmation", true));
        assertThat(auth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) data(auth).get("auth")).get("requireEmailConfirmation"))
                .isEqualTo(true);
        // возврат выключенного гейта для остальных тестов класса
        exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/auth",
                Map.of("requireEmailConfirmation", false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void telegramTokenIsWriteOnlyAndRulesPersist() {
        String admin = login("admin@gustomeat.by", "change-me");

        ResponseEntity<ApiResponse> updated = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/notifications", Map.of(
                        "telegramBotToken", "123456:TEST-TOKEN",
                        "rules", Map.of("ORDER_CREATED", false, "INVOICE_ISSUED", false)));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> notifications = (Map<String, Object>) data(updated)
                .get("notifications");
        assertThat(notifications.get("telegramTokenSet")).isEqualTo(true);
        assertThat(notifications.get("telegramTokenSource")).isEqualTo("settings");
        Map<String, Object> rules = (Map<String, Object>) notifications.get("rules");
        assertThat(rules.get("ORDER_CREATED")).isEqualTo(false);
        assertThat(rules.get("ORDER_STATUS_CHANGED")).isEqualTo(true);

        // токен никогда не возвращается (write-only)
        assertThat(updated.getBody().toString()).doesNotContain("TEST-TOKEN");

        // очистка токена пустой строкой
        ResponseEntity<ApiResponse> cleared = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/settings/notifications",
                Map.of("telegramBotToken", "", "rules", Map.of()));
        assertThat(((Map<String, Object>) data(cleared).get("notifications"))
                .get("telegramTokenSource")).isEqualTo("env");

        String stored = jdbcTemplate.queryForObject(
                "select count(*) from settings where key = 'telegram.bot_token'", Integer.class)
                .toString();
        assertThat(stored).isEqualTo("0");
    }

    // ----- журнал аудита ----------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void auditJournalFiltersAndRights() {
        String admin = login("admin@gustomeat.by", "change-me");
        exchange(admin, HttpMethod.PUT, "/api/v1/admin/settings/documents",
                Map.of("seriesTtn", "A", "seriesTn", "A", "vatDefault", 10));

        ResponseEntity<ApiResponse> journal = exchange(admin, HttpMethod.GET,
                "/api/v1/admin/audit?action=SETTINGS_UPDATE&page=0&size=10", null);
        assertThat(journal.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) journal.getBody().getData();
        assertThat(entries).isNotEmpty();
        Map<String, Object> entry = entries.get(0);
        assertThat(entry.get("action").toString()).startsWith("SETTINGS_UPDATE");
        assertThat(entry.get("actorEmail")).isNotNull();

        ResponseEntity<ApiResponse> byTarget = exchange(admin, HttpMethod.GET,
                "/api/v1/admin/audit?targetType=settings&dateFrom="
                        + java.time.LocalDate.now().toString(), null);
        assertThat(byTarget.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<Map<String, Object>>) byTarget.getBody().getData()).size())
                .isGreaterThanOrEqualTo(entries.size());

        // аудит — только ADMIN (матрица 2.1)
        String manager = createStaffAndLogin(Role.MANAGER);
        assertThat(exchange(manager, HttpMethod.GET, "/api/v1/admin/audit", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- дашборд процессов --------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void operationsDashboardCountsProcesses() {
        String admin = login("admin@gustomeat.by", "change-me");

        Company company = createCompanyWithClient("ООО «Дашборд»", "190444555");
        Map<String, Object> order = createLegalOrder(admin, company.getId());
        ResponseEntity<ApiResponse> invoice = exchange(admin, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", order.get("id").toString()));
        assertThat(invoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = ((Map<String, Object>) invoice.getBody().getData()).get("id").toString();
        exchange(admin, HttpMethod.POST, "/api/v1/invoices/" + invoiceId + "/issue", null);

        // заявка с сайта за сегодня
        jdbcTemplate.update("insert into site_requests (name, type, status) values (?, 'CALLBACK', 'NEW')",
                "Тестовая заявка " + UUID.randomUUID());
        // просроченная задача менеджера
        User manager = userRepository.save(User.builder()
                .email("mgr-" + UUID.randomUUID() + "@test.by")
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Менеджер Просрочка")
                .role(Role.MANAGER)
                .active(true)
                .build());
        jdbcTemplate.update("""
                        insert into crm_tasks (assignee_id, title, due_date, status)
                        values (?, 'Просроченная задача', now() - interval '1 day', 'OPEN')
                        """,
                manager.getId());
        // позиция ниже min_stock
        UUID productId = productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay")
                .orElseThrow().getId();
        jdbcTemplate.update("update products set min_stock = 100 where id = ?", productId);
        jdbcTemplate.update("update stock_balances set quantity = 2, reserved = 0 "
                + "where product_id = ?", productId);

        ResponseEntity<ApiResponse> response = exchange(admin, HttpMethod.GET,
                "/api/v1/admin/operations/dashboard", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dashboard = data(response);
        assertThat(((Number) dashboard.get("ordersToday")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) dashboard.get("requestsToday")).longValue()).isGreaterThanOrEqualTo(1);

        Map<String, Object> unpaid = (Map<String, Object>) dashboard.get("unpaidInvoices");
        assertThat(((Number) unpaid.get("count")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) unpaid.get("outstanding")).doubleValue()).isGreaterThan(0);

        List<Map<String, Object>> lowStock = (List<Map<String, Object>>) dashboard.get("lowStock");
        assertThat(lowStock).anySatisfy(item -> {
            assertThat(item.get("productId")).isEqualTo(productId.toString());
            assertThat(((Number) item.get("available")).doubleValue()).isLessThan(100);
        });

        List<Map<String, Object>> overdue = (List<Map<String, Object>>) dashboard.get("overdueTasks");
        assertThat(overdue).anySatisfy(task -> {
            assertThat(task.get("title")).isEqualTo("Просроченная задача");
            assertThat(task.get("assigneeName")).isEqualTo("Менеджер Просрочка");
        });

        // дашборд доступен и бухгалтеру (матрица 2.1 «Дашборды и статистика»)
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);
        assertThat(exchange(accountant, HttpMethod.GET, "/api/v1/admin/operations/dashboard", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        jdbcTemplate.update("update products set min_stock = 0 where id = ?", productId);
    }

    // ----- предпросмотр импорта -------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void importPreviewReportsRowsWithoutWriting() {
        String admin = login("admin@gustomeat.by", "change-me");

        byte[] file = xlsx(List.of(
                Map.of("sku", "steyk-ribay", "value", "25.5"),
                Map.of("sku", "unknown-sku", "value", "10"),
                Map.of("sku", "steyk-ribay", "value", "abc")));

        Integer filesBefore = jdbcTemplate.queryForObject(
                "select count(*) from integration_files", Integer.class);

        ResponseEntity<ApiResponse> response = preview(admin, "prices", file);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> previewData = data(response);
        assertThat(previewData.get("type")).isEqualTo("PRICES");
        assertThat(((Number) previewData.get("rowsTotal")).intValue()).isEqualTo(3);
        assertThat(((Number) previewData.get("errorsCount")).intValue()).isEqualTo(2);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) previewData.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("ok")).isEqualTo(true);
        assertThat((String) rows.get(1).get("message")).contains("Товар не найден");
        assertThat((String) rows.get(2).get("message")).contains("Некорректное значение");

        // предпросмотр ничего не пишет
        Integer filesAfter = jdbcTemplate.queryForObject(
                "select count(*) from integration_files", Integer.class);
        assertThat(filesAfter).isEqualTo(filesBefore);

        // права как у импорта (S35): ADMIN/ACCOUNTANT, менеджеру 403
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);
        assertThat(preview(accountant, "stock", file).getStatusCode()).isEqualTo(HttpStatus.OK);
        String manager = createStaffAndLogin(Role.MANAGER);
        assertThat(preview(manager, "prices", file).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
