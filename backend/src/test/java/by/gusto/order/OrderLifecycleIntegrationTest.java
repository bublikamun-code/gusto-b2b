package by.gusto.order;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
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
 * S22: жизненный цикл заказа — статус-машина, права, отмена с релизом резерва,
 * пул «не назначено» и «взять в работу», записи в audit_log.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderLifecycleIntegrationTest {

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

    // ----- пользователи ---------------------------------------------------------

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    /** Менеджер создаётся напрямую в БД: саморегистрация даёт только роль клиента. */
    private String createManagerAndLogin(String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("manager-pass"))
                .fullName("Менеджер " + email)
                .role(Role.MANAGER)
                .active(true)
                .build());
        return login(email, "manager-pass");
    }

    private String registerAndLoginIndividual() {
        String email = "buyer-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Покупатель"), ApiResponse.class);
        return login(email, "password123");
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

    /** Розничный заказ 2 кг стейка: попадает в пул «не назначено» и резервирует склад. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createRetailOrder(String buyerToken) {
        ResponseEntity<ApiResponse> created = exchange(buyerToken, HttpMethod.POST, "/api/v1/orders", Map.of(
                "deliveryType", "PICKUP",
                "recipientName", "Иван Покупатель",
                "recipientPhone", "+375 29 111-22-33",
                "items", List.of(Map.of(
                        "productId", productId("steyk-ribay").toString(),
                        "quantity", 2))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) created.getBody().getData();
    }

    private BigDecimal reserved() {
        return balanceRepository
                .findById(new StockBalance.StockBalanceId(productId("steyk-ribay"), LOCATION))
                .orElseThrow().getReserved();
    }

    private int auditRows(UUID orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where target_type = 'order' and target_id = ?",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> orderBody(ResponseEntity<ApiResponse> response) {
        return (Map<String, Object>) response.getBody().getData();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listBody(ResponseEntity<ApiResponse> response) {
        return (List<Map<String, Object>>) response.getBody().getData();
    }

    // ----- тесты ----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void statusMachineHappyPathFromUnassignedPoolToCompleted() {
        String buyer = registerAndLoginIndividual();
        String manager = createManagerAndLogin("mgr-happy-" + UUID.randomUUID() + "@test.by");
        Map<String, Object> order = createRetailOrder(buyer);
        String orderId = order.get("id").toString();
        assertThat(order.get("managerId")).isNull();

        // заказ в пуле «не назначено»
        ResponseEntity<ApiResponse> pool = exchange(manager, HttpMethod.GET,
                "/api/v1/manager/orders?scope=unassigned", null);
        assertThat(listBody(pool)).extracting(o -> o.get("id")).contains(orderId);

        // взять в работу
        ResponseEntity<ApiResponse> taken = exchange(manager, HttpMethod.POST,
                "/api/v1/manager/orders/" + orderId + "/take", null);
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderBody(taken).get("managerId")).isNotNull();

        // второй менеджер уже не может взять
        String manager2 = createManagerAndLogin("mgr-second-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> again = exchange(manager2, HttpMethod.POST,
                "/api/v1/manager/orders/" + orderId + "/take", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().getError().getCode()).isEqualTo("ORDER_ALREADY_TAKEN");

        // manager2 не видит заказ в «своих», manager1 видит
        assertThat(listBody(exchange(manager2, HttpMethod.GET,
                "/api/v1/manager/orders?scope=mine", null))).isEmpty();
        assertThat(listBody(exchange(manager, HttpMethod.GET,
                "/api/v1/manager/orders?scope=mine", null))).isNotEmpty();

        // полный путь статус-машины выполняет назначенный менеджер
        for (String status : List.of("CONFIRMED", "PROCESSING", "READY", "SHIPPED", "COMPLETED")) {
            ResponseEntity<ApiResponse> changed = exchange(manager, HttpMethod.PUT,
                    "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", status));
            assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(orderBody(changed).get("status")).isEqualTo(status);
        }

        // из терминального COMPLETED перехода нет
        ResponseEntity<ApiResponse> terminal = exchange(manager, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CANCELLED"));
        assertThat(terminal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(terminal.getBody().getError().getCode()).isEqualTo("ORDER_STATUS_TRANSITION");

        // резерв при COMPLETED не тронут, аудит записан
        assertThat(reserved()).isEqualByComparingTo("2.000");
        assertThat(auditRows(UUID.fromString(orderId))).isGreaterThanOrEqualTo(6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidTransitionIsRejected() {
        String buyer = registerAndLoginIndividual();
        String manager = createManagerAndLogin("mgr-invalid-" + UUID.randomUUID() + "@test.by");
        Map<String, Object> order = createRetailOrder(buyer);
        String orderId = order.get("id").toString();

        // не назначенный менеджер не может менять статус чужого (пул) заказа
        String manager2 = createManagerAndLogin("mgr-alien-" + UUID.randomUUID() + "@test.by");
        ResponseEntity<ApiResponse> alien = exchange(manager2, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
        assertThat(alien.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // назначенный менеджер берёт заказ и пробует недопустимые переходы
        exchange(manager, HttpMethod.POST, "/api/v1/manager/orders/" + orderId + "/take", null);

        // NEW → SHIPPED: пропуск стадий запрещён
        ResponseEntity<ApiResponse> skip = exchange(manager, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "SHIPPED"));
        assertThat(skip.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(skip.getBody().getError().getCode()).isEqualTo("ORDER_STATUS_TRANSITION");

        // клиент не может менять статус своего заказа
        ResponseEntity<ApiResponse> byClient = exchange(buyer, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
        assertThat(byClient.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelReleasesReserveOnce() {
        String buyer = registerAndLoginIndividual();
        String manager = createManagerAndLogin("mgr-cancel-" + UUID.randomUUID() + "@test.by");
        Map<String, Object> order = createRetailOrder(buyer);
        String orderId = order.get("id").toString();
        assertThat(reserved()).isEqualByComparingTo("2.000");

        exchange(manager, HttpMethod.POST, "/api/v1/manager/orders/" + orderId + "/take", null);
        ResponseEntity<ApiResponse> cancelled = exchange(manager, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CANCELLED"));
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderBody(cancelled).get("status")).isEqualTo("CANCELLED");

        // резерв разблокирован
        assertThat(reserved()).isEqualByComparingTo("0");

        // повторная отмена и смена статуса невозможны
        ResponseEntity<ApiResponse> recancel = exchange(manager, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CANCELLED"));
        assertThat(recancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<ApiResponse> reopen = exchange(manager, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "NEW"));
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reserved()).isEqualByComparingTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminCanManageAnyOrderAndManagerCannotUseAllScope() {
        String buyer = registerAndLoginIndividual();
        Map<String, Object> order = createRetailOrder(buyer);
        String orderId = order.get("id").toString();
        String manager = createManagerAndLogin("mgr-scope-" + UUID.randomUUID() + "@test.by");

        // менеджеру scope=all запрещён
        ResponseEntity<ApiResponse> scopeAll = exchange(manager, HttpMethod.GET,
                "/api/v1/manager/orders?scope=all", null);
        assertThat(scopeAll.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // админ видит пул и берёт заказ в работу без ограничений
        String admin = login("admin@gustomeat.by", "change-me");
        ResponseEntity<ApiResponse> adminPool = exchange(admin, HttpMethod.GET,
                "/api/v1/manager/orders?scope=unassigned", null);
        assertThat(listBody(adminPool)).extracting(o -> o.get("id")).contains(orderId);

        ResponseEntity<ApiResponse> confirmed = exchange(admin, HttpMethod.PUT,
                "/api/v1/manager/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);

        // клиент не допускается к менеджерскому списку
        ResponseEntity<ApiResponse> byClient = exchange(buyer, HttpMethod.GET,
                "/api/v1/manager/orders", null);
        assertThat(byClient.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
