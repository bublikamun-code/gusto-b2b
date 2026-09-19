package by.gusto.notification;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.notification.repository.NotificationSubscriptionRepository;
import by.gusto.notification.telegram.TelegramNotificationChannel;
import by.gusto.outbox.entity.OutboxMessage;
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
 * S32: Telegram — привязка чата по коду через вебхук /start, список/отписка,
 * маршрутизация уведомлений (адресаты по типу события), поведение при
 * выключенной интеграции (нет токена — события пропускаются без ошибок).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "telegram.bot-token=",
                "telegram.webhook-secret=test-secret"
        })
@Testcontainers
class TelegramIntegrationTest {

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
    private NotificationSubscriptionRepository subscriptionRepository;

    @Autowired
    private TelegramNotificationChannel telegramChannel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ----- helpers --------------------------------------------------------------

    private String createStaffAndLogin(Role role, String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник " + role)
                .role(role)
                .active(true)
                .build());
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "staff-pass"), ApiResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) login.getBody().getData()).get("accessToken").toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String webhookBody(String text) {
        return "{\"message\":{\"chat\":{\"id\":\"424242\"},\"text\":\"" + text + "\"}}";
    }

    @Test
    @SuppressWarnings("unchecked")
    void linkByWebhookCodeAndUnsubscribe() {
        String manager = createStaffAndLogin(Role.MANAGER, "mgr-tg-" + UUID.randomUUID() + "@test.by");

        // вебхук без секрета → 403
        ResponseEntity<String> noSecret = restTemplate.postForEntity(
                "/api/v1/notifications/telegram/webhook", httpJson(webhookBody("/start 123456")), String.class);
        assertThat(noSecret.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // генерируем код и «отправляем» боту /start <КОД> с секретом
        ResponseEntity<ApiResponse> codeResponse = restTemplate.exchange(
                "/api/v1/notifications/telegram/code", HttpMethod.POST,
                new HttpEntity<>(authHeaders(manager)), ApiResponse.class);
        assertThat(codeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = ((Map<String, Object>) codeResponse.getBody().getData()).get("code").toString();

        ResponseEntity<String> linked = restTemplate.postForEntity(
                "/api/v1/notifications/telegram/webhook?secret=test-secret",
                httpJson(webhookBody("/start " + code)), String.class);
        assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);

        // подписка создана и видна в списке
        ResponseEntity<ApiResponse> subscriptions = restTemplate.exchange(
                "/api/v1/notifications/subscriptions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(manager)), ApiResponse.class);
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) subscriptions.getBody().getData();
        assertThat(items).extracting(s -> s.get("destination")).contains("424242");
        assertThat(items).extracting(s -> s.get("channel")).contains("TELEGRAM");

        // повторный /start с тем же кодом не работает (одноразовый)
        ResponseEntity<String> replay = restTemplate.postForEntity(
                "/api/v1/notifications/telegram/webhook?secret=test-secret",
                httpJson(webhookBody("/start " + code)), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        // подписка не задублировалась (unique по user+channel+destination)
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from notification_subscriptions where destination = '424242'",
                Integer.class);
        assertThat(count).isEqualTo(1);

        // отписка
        UUID subscriptionId = UUID.fromString(items.get(0).get("id").toString());
        ResponseEntity<ApiResponse> off = restTemplate.exchange(
                "/api/v1/notifications/subscriptions/" + subscriptionId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(manager)), ApiResponse.class);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(subscriptionRepository.findById(subscriptionId).orElseThrow().isActive()).isFalse();
    }

    private HttpEntity<String> httpJson(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void disabledIntegrationSkipsEventsAndRoutingResolvesManagers() {
        String manager = createStaffAndLogin(Role.MANAGER, "mgr-route-" + UUID.randomUUID() + "@test.by");
        restTemplate.exchange("/api/v1/notifications/telegram/code", HttpMethod.POST,
                new HttpEntity<>(authHeaders(manager)), ApiResponse.class);

        // привязываем чат через редис напрямую (код создаётся тем же сервисом)
        UUID managerId = userRepository.findByEmailIgnoreCase(
                userRepository.findAll().stream()
                        .filter(u -> u.getEmail().startsWith("mgr-route-")).findFirst().orElseThrow().getEmail())
                .orElseThrow().getId();
        subscriptionRepository.save(by.gusto.notification.entity.NotificationSubscription.builder()
                .userId(managerId)
                .channel(by.gusto.notification.entity.NotificationSubscription.Channel.TELEGRAM)
                .destination("100500")
                .build());

        // интеграция выключена → канал возвращает успех без отправки
        OutboxMessage message = OutboxMessage.builder()
                .aggregateType("order")
                .aggregateId(UUID.randomUUID())
                .type("ORDER_CREATED")
                .payload(Map.of("number", "З-2026-00099", "retail", true, "totalAmount", "85.00"))
                .build();
        assertThat(telegramChannel.send(message)).isTrue();
        assertThat(telegramChannel.supports("ORDER_CREATED")).isTrue();
        assertThat(telegramChannel.supports("INVOICE_ISSUED")).isTrue();
        assertThat(telegramChannel.supports("UNKNOWN_EVENT")).isFalse();

        // маршрутизация: розничный заказ — адресат подписанный менеджер
        List<String> chatIds = telegramChannel.resolveChatIds("ORDER_CREATED",
                message.getAggregateId(), message.getPayload());
        assertThat(chatIds).contains("100500");
    }
}
