package by.gusto.notification;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.notification.email.EmailNotificationChannel;
import by.gusto.notification.entity.NotificationSubscription;
import by.gusto.notification.repository.NotificationSubscriptionRepository;
import by.gusto.outbox.entity.OutboxMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * S33: Email — канал обрабатывает EMAIL_* и клиентские события; правило 2.6
 * «клиенту письмо — только если нет Telegram-подписки»; при выключенном SMTP
 * события пропускаются (заявки/уведомления не ломаются).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.mail.host=",
                "telegram.bot-token="
        })
@Testcontainers
class EmailIntegrationTest {

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
    private EmailNotificationChannel emailChannel;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void disabledSmtpSkipsEmailEvents() {
        assertThat(emailChannel.supports("EMAIL_CONFIRMATION")).isTrue();
        assertThat(emailChannel.supports("EMAIL_PASSWORD_RESET")).isTrue();
        assertThat(emailChannel.supports("ORDER_CREATED")).isFalse();

        OutboxMessage message = OutboxMessage.builder()
                .aggregateType("user")
                .aggregateId(UUID.randomUUID())
                .type("EMAIL_CONFIRMATION")
                .payload(Map.of(
                        "to", "someone@test.by",
                        "subject", "Подтверждение email — Густо",
                        "confirmationUrl", "http://localhost:5173/confirm-email?token=abc"))
                .build();
        // SMTP выключен → канал возвращает успех, ничего не отправляя
        assertThat(emailChannel.send(message)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientWithoutTelegramGetsEmailAndWithTelegramSkipped() {
        String adminToken = createStaffAndLogin(Role.ADMIN, "admin-email-" + UUID.randomUUID() + "@test.by");

        // два клиента: с Telegram-подпиской и без
        UUID withTgId = createClient("client-tg-" + UUID.randomUUID() + "@test.by");
        UUID withoutTgId = createClient("client-plain-" + UUID.randomUUID() + "@test.by");
        subscriptionRepository.save(NotificationSubscription.builder()
                .userId(withTgId)
                .channel(NotificationSubscription.Channel.TELEGRAM)
                .destination("5577")
                .build());

        assertThat(emailChannel.resolveClientEmails("ORDER_STATUS_CHANGED", withTgId)).isEmpty();
        assertThat(emailChannel.resolveClientEmails("ORDER_STATUS_CHANGED", withoutTgId))
                .containsExactly(userRepository.findById(withoutTgId).orElseThrow().getEmail());

        // подписки видны в API клиента
        String clientToken = login(userRepository.findById(withTgId).orElseThrow().getEmail(),
                "password123");
        ResponseEntity<ApiResponse> subscriptions = restTemplate.exchange(
                "/api/v1/notifications/subscriptions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(clientToken)), ApiResponse.class);
        assertThat(subscriptions.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) subscriptions.getBody().getData();
        assertThat(items).extracting(s -> s.get("destination")).contains("5577");

        // админ тоже может посмотреть свои подписки (пусто) — эндпоинт работает
        ResponseEntity<ApiResponse> adminSubs = restTemplate.exchange(
                "/api/v1/notifications/subscriptions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), ApiResponse.class);
        assertThat(adminSubs.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----- helpers --------------------------------------------------------------

    private UUID createClient(String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Клиент Email")
                .role(Role.CUSTOMER_LEGAL)
                .active(true)
                .emailConfirmedAt(java.time.Instant.now())
                .build());
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private String createStaffAndLogin(Role role, String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник")
                .role(role)
                .active(true)
                .build());
        return login(email, "staff-pass");
    }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
