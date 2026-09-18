package by.gusto.invoice;

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
 * S24: счета — создание из заказа со снапшотами, НДС расчётно, нумерация СЧ-N,
 * жизненный цикл, права и неизменность снапшота после смены реквизитов (приёмка).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InvoiceIntegrationTest {

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
        // тесты класса делят одну БД: возвращаем seed-реквизиты продавца (V2),
        // которые меняет тест неизменности снапшота
        jdbcTemplate.update("insert into settings (key, value) values ('seller.requisites', ?::jsonb) "
                        + "on conflict (key) do update set value = excluded.value",
                "{\"name\": \"ЧТУП «ЛорСан»\", \"unp\": \"191536521\", \"address\": "
                        + "\"220028 г. Минск, ул. Бородинская, д. 1Б, пом. 14, РБ\", \"bank_account\": \"\", "
                        + "\"bank_name\": \"\", \"bank_bic\": \"\"}");
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

    /** Компания + привязанный пользователь-юрлицо; возвращает email клиента. */
    private Company createCompanyWithClient(String name, String unp, java.util.function.Consumer<String> emailSink) {
        Company company = companyRepository.save(Company.builder()
                .name(name)
                .unp(unp)
                .legalAddress("г. Минск, ул. Тестовая, 1")
                .build());
        String email = "legal-" + UUID.randomUUID() + "@test.by";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Клиент Юрлицо")
                .role(Role.CUSTOMER_LEGAL)
                .companyId(company.getId())
                .active(true)
                .emailConfirmedAt(java.time.Instant.now())
                .build());
        emailSink.accept(email);
        return company;
    }

    /** Заказ от имени юрлица, создаёт админ (контракт S20). */
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> createInvoice(String actorToken, UUID orderId, HttpStatus expected) {
        ResponseEntity<ApiResponse> created = exchange(actorToken, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", orderId.toString()));
        assertThat(created.getStatusCode()).isEqualTo(expected);
        return created.getStatusCode() == HttpStatus.CREATED
                ? (Map<String, Object>) created.getBody().getData() : null;
    }

    // ----- тесты ----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void createFromOrderSnapshotsSellerBuyerItemsAndCalculatesVat() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «ТестТрейд»", "192953686", email -> { });
        Map<String, Object> order = createLegalOrder(admin, company.getId());

        Map<String, Object> invoice = createInvoice(admin, UUID.fromString(order.get("id").toString()),
                HttpStatus.CREATED);

        // нумерация СЧ-N (2.2) и дата
        assertThat((String) invoice.get("number")).matches("СЧ-\\d+");
        assertThat((String) invoice.get("displayNumber")).matches("СЧ-\\d+ от \\d{2}\\.\\d{2}\\.\\d{4}");
        assertThat(invoice.get("status")).isEqualTo("DRAFT");

        // снапшот покупателя — реквизиты компании
        Map<String, Object> buyer = (Map<String, Object>) invoice.get("buyerSnapshot");
        assertThat(buyer.get("name")).isEqualTo("ООО «ТестТрейд»");
        assertThat(buyer.get("unp")).isEqualTo("192953686");

        // снапшот продавца — из settings (V2 seed: ЧТУП «ЛорСан»)
        Map<String, Object> seller = (Map<String, Object>) invoice.get("sellerSnapshot");
        assertThat(seller.get("name")).isEqualTo("ЧТУП «ЛорСан»");
        assertThat(seller.get("unp")).isEqualTo("191536521");

        // позиции — снапшот товара из заказа; НДС расчётно: 85.00 × 10/110 = 7.73
        List<Map<String, Object>> items = (List<Map<String, Object>>) invoice.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("sku")).isEqualTo("steyk-ribay");
        assertThat(((Number) items.get(0).get("total")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) items.get(0).get("vat")).doubleValue()).isEqualTo(7.73);
        assertThat(((Number) invoice.get("totalAmount")).doubleValue()).isEqualTo(85.00);
        assertThat(((Number) invoice.get("totalVat")).doubleValue()).isEqualTo(7.73);

        // создание счёта — в audit_log
        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where target_type = 'invoice' and action = 'INVOICE_CREATE'",
                Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void invoiceSnapshotDoesNotChangeWhenRequisitesChange() {
        // приёмка S24: смена реквизитов не переписывает уже созданный счёт
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Снапшот»", "190000001", email -> { });
        Map<String, Object> order = createLegalOrder(admin, company.getId());
        Map<String, Object> invoice = createInvoice(admin, UUID.fromString(order.get("id").toString()),
                HttpStatus.CREATED);
        String numberBefore = (String) invoice.get("number");
        Map<String, Object> buyerBefore = (Map<String, Object>) invoice.get("buyerSnapshot");
        Map<String, Object> sellerBefore = (Map<String, Object>) invoice.get("sellerSnapshot");

        // меняем реквизиты продавца и данные компании
        jdbcTemplate.update("update settings set value = ?::jsonb where key = 'seller.requisites'",
                "{\"name\": \"ООО «НовыеРеквизиты»\", \"unp\": \"999999999\", \"address\": \"Брест\"}");
        Company companyRef = companyRepository.findById(company.getId()).orElseThrow();
        companyRef.setName("ООО «Переименование»");
        companyRef.setUnp("190000002");
        companyRepository.save(companyRef);

        ResponseEntity<ApiResponse> reread = exchange(admin, HttpMethod.GET,
                "/api/v1/invoices/" + invoice.get("id"), null);
        Map<String, Object> after = data(reread);
        Map<String, Object> buyerAfter = (Map<String, Object>) after.get("buyerSnapshot");
        Map<String, Object> sellerAfter = (Map<String, Object>) after.get("sellerSnapshot");

        assertThat(after.get("number")).isEqualTo(numberBefore);
        assertThat(buyerAfter).isEqualTo(buyerBefore);
        assertThat(sellerAfter).isEqualTo(sellerBefore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lifecycleIssueCancelAndRecreate() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Цикл»", "190000003", email -> { });
        Map<String, Object> order = createLegalOrder(admin, company.getId());
        UUID orderId = UUID.fromString(order.get("id").toString());

        String accountant = createStaffAndLogin(Role.ACCOUNTANT, "acc-" + UUID.randomUUID() + "@test.by");

        // выпуск из черновика + событие уведомления (2.6)
        Map<String, Object> draft = createInvoice(accountant, orderId, HttpStatus.CREATED);
        ResponseEntity<ApiResponse> issued = exchange(accountant, HttpMethod.POST,
                "/api/v1/invoices/" + draft.get("id") + "/issue", null);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(issued).get("status")).isEqualTo("ISSUED");
        Integer issuedEvents = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'INVOICE_ISSUED'", Integer.class);
        assertThat(issuedEvents).isGreaterThanOrEqualTo(1);

        // повторный выпуск → 409 INVOICE_INVALID_STATE
        ResponseEntity<ApiResponse> reissue = exchange(accountant, HttpMethod.POST,
                "/api/v1/invoices/" + draft.get("id") + "/issue", null);
        assertThat(reissue.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reissue.getBody().getError().getCode()).isEqualTo("INVOICE_INVALID_STATE");

        // второй активный счёт по заказу → 409
        ResponseEntity<ApiResponse> duplicate = exchange(admin, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", orderId.toString()));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().getError().getCode()).isEqualTo("INVOICE_ALREADY_EXISTS");

        // отмена выпущенного → после неё можно выставить новый
        ResponseEntity<ApiResponse> cancelled = exchange(admin, HttpMethod.POST,
                "/api/v1/invoices/" + draft.get("id") + "/cancel", null);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(cancelled).get("status")).isEqualTo("CANCELLED");

        Map<String, Object> second = createInvoice(admin, orderId, HttpStatus.CREATED);
        assertThat((String) second.get("number")).isNotEqualTo(draft.get("number"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rightsAndRetailRestrictions() {
        String admin = login("admin@gustomeat.by", "change-me");
        String[] legalEmail = new String[1];
        Company company = createCompanyWithClient("ООО «Права»", "190000004",
                email -> legalEmail[0] = email);
        String legalUser = login(legalEmail[0], "password123");
        Map<String, Object> order = createLegalOrder(admin, company.getId());
        UUID orderId = UUID.fromString(order.get("id").toString());

        // клиент не создаёт счета (матрица 2.1)
        ResponseEntity<ApiResponse> byClient = exchange(legalUser, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", orderId.toString()));
        assertThat(byClient.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // клиент видит счёт своей компании в кабинете
        Map<String, Object> invoice = createInvoice(admin, orderId, HttpStatus.CREATED);
        exchange(admin, HttpMethod.POST, "/api/v1/invoices/" + invoice.get("id") + "/issue", null);
        ResponseEntity<ApiResponse> cabinet = exchange(legalUser, HttpMethod.GET, "/api/v1/cabinet/invoices", null);
        assertThat(cabinet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listData(cabinet))
                .extracting(i -> i.get("id"))
                .contains(invoice.get("id"));

        // менеджер без отношения к компании не видит счёт в списке
        String outsider = createStaffAndLogin(Role.MANAGER, "mgr-out-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> managerList = exchange(outsider, HttpMethod.GET, "/api/v1/invoices", null);
        assertThat(listData(managerList))
                .extracting(i -> i.get("id"))
                .doesNotContain(invoice.get("id"));

        // розничному заказу счёт не выставляется
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
        ResponseEntity<ApiResponse> retailInvoice = exchange(admin, HttpMethod.POST, "/api/v1/invoices",
                Map.of("orderId", retailOrderId.toString()));
        assertThat(retailInvoice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
