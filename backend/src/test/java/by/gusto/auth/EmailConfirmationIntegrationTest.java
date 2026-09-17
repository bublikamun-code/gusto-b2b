package by.gusto.auth;

import by.gusto.common.api.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S08.1: подтверждение email при саморегистрации физлица.
 * Гейт auth.require_email_confirmation по умолчанию выключен — поведение как до S08.1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EmailConfirmationIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        setGate(false);
    }

    @AfterEach
    void tearDown() {
        setGate(false);
    }

    private void setGate(boolean enabled) {
        jdbcTemplate.update(
                "insert into settings (key, value) values ('auth.require_email_confirmation', ?::jsonb) "
                        + "on conflict (key) do update set value = ?::jsonb",
                Boolean.toString(enabled), Boolean.toString(enabled));
    }

    private ResponseEntity<ApiResponse> register(String email) {
        Map<String, String> request = Map.of(
                "email", email,
                "password", "password123",
                "fullName", "Иван Иванов");
        return restTemplate.postForEntity("/api/v1/auth/register", request, ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> login(String email) {
        Map<String, String> request = Map.of("email", email, "password", "password123");
        return restTemplate.postForEntity("/api/v1/auth/login", request, ApiResponse.class);
    }

    @SuppressWarnings("unchecked")
    private String extractTokenFromOutbox(String email) {
        List<String> payloads = jdbcTemplate.queryForList(
                "select payload::text from outbox_messages where type = 'EMAIL_CONFIRMATION' "
                        + "and payload->>'to' = ? order by created_at desc limit 1",
                String.class, email);
        assertThat(payloads).hasSize(1);
        Matcher matcher = Pattern.compile("\"confirmationUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(payloads.get(0));
        assertThat(matcher.find()).isTrue();
        String url = matcher.group(1);
        assertThat(url).startsWith("http");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    @Test
    void gateOffRegisterAndLoginWorkAsBefore() {
        ResponseEntity<ApiResponse> registerResponse = register("gateoff@test.by");
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody().getData()).isNotNull();

        ResponseEntity<ApiResponse> loginResponse = login("gateoff@test.by");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void gateOnBlocksUnconfirmedUserAndConfirmationUnlocksLogin() {
        setGate(true);

        ResponseEntity<ApiResponse> registerResponse = register("confirm@test.by");
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiResponse> blocked = login("confirm@test.by");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().getError().getCode()).isEqualTo("AUTH_EMAIL_NOT_CONFIRMED");

        String rawToken = extractTokenFromOutbox("confirm@test.by");
        Map<String, String> confirmRequest = Map.of("token", rawToken);
        ResponseEntity<ApiResponse> confirmResponse = restTemplate.postForEntity(
                "/api/v1/auth/email/confirm", confirmRequest, ApiResponse.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse> loginResponse = login("confirm@test.by");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void gateOnDoesNotBlockAdminCreatedUsers() {
        setGate(true);
        // admin@gustomeat.by заведён сид-миграцией V2 (не саморегистрация)
        Map<String, String> request = Map.of("email", "admin@gustomeat.by", "password", "change-me");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmRejectsInvalidAndUsedTokens() {
        Map<String, String> badRequest = Map.of("token", "garbage-token");
        ResponseEntity<ApiResponse> bad = restTemplate.postForEntity(
                "/api/v1/auth/email/confirm", badRequest, ApiResponse.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().getError().getCode()).isEqualTo("AUTH_EMAIL_CONFIRM_TOKEN_INVALID");

        setGate(true);
        register("reuse@test.by");
        String rawToken = extractTokenFromOutbox("reuse@test.by");

        ResponseEntity<ApiResponse> first = restTemplate.postForEntity(
                "/api/v1/auth/email/confirm", Map.of("token", rawToken), ApiResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse> second = restTemplate.postForEntity(
                "/api/v1/auth/email/confirm", Map.of("token", rawToken), ApiResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().getError().getCode()).isEqualTo("AUTH_EMAIL_CONFIRM_TOKEN_INVALID");
    }

    @Test
    void resendCreatesNewOutboxMessageAndStaysGeneric() {
        setGate(true);
        register("resend@test.by");

        Integer before = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'EMAIL_CONFIRMATION' and payload->>'to' = ?",
                Integer.class, "resend@test.by");

        ResponseEntity<ApiResponse> resend = restTemplate.postForEntity(
                "/api/v1/auth/email/resend", Map.of("email", "resend@test.by"), ApiResponse.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer after = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'EMAIL_CONFIRMATION' and payload->>'to' = ?",
                Integer.class, "resend@test.by");
        assertThat(after).isEqualTo(before + 1);

        // Неизвестный адрес — тот же обезличенный 200, ничего не создаём
        ResponseEntity<ApiResponse> unknown = restTemplate.postForEntity(
                "/api/v1/auth/email/resend", Map.of("email", "nobody@test.by"), ApiResponse.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer unknownCount = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'EMAIL_CONFIRMATION' and payload->>'to' = ?",
                Integer.class, "nobody@test.by");
        assertThat(unknownCount).isZero();
    }

    @Test
    void resendIsRateLimited() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                    "/api/v1/auth/email/resend", Map.of("email", "limited@test.by"), ApiResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("email", "limited@test.by"), headers);
        ResponseEntity<ApiResponse> blocked = restTemplate.postForEntity(
                "/api/v1/auth/email/resend", entity, ApiResponse.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().getError().getCode()).isEqualTo("RATE_LIMITED");
    }
}
