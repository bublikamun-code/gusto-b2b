package by.gusto.request;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.outbox.channel.OutboxChannel;
import by.gusto.outbox.entity.OutboxMessage;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import by.gusto.outbox.service.OutboxPoller;
import by.gusto.request.repository.SiteRequestRepository;
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
 * S31: заявки с сайта — публичное создание с идемпотентностью и rate limit,
 * автоконвертация в лид пул «не назначено» (2.7), outbox-поллер: доставка,
 * ретраи по backoff, FAILED после лимита попыток.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SiteRequestIntegrationTest {

    /** Канал, «падающий» для типов FAIL_TEST_*: симуляция выключенного SMTP (S33). */
    @TestConfiguration
    static class FailingChannelConfig {
        @Bean
        OutboxChannel failingChannel() {
            return new OutboxChannel() {
                @Override
                public boolean supports(String type) {
                    return type.startsWith("FAIL_TEST");
                }

                @Override
                public boolean send(OutboxMessage message) {
                    return false;
                }
            };
        }
    }

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
    private SiteRequestRepository siteRequestRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ----- helpers --------------------------------------------------------------

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

    @SuppressWarnings("unchecked")
    private String createStaffAndLogin(Role role, String email) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник")
                .role(role)
                .active(true)
                .build());
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "staff-pass"), ApiResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) login.getBody().getData()).get("accessToken").toString();
    }

    private ResponseEntity<ApiResponse> createRequest(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        Map<String, Object> body = Map.of(
                "name", "Посетитель сайта",
                "phone", "+375 29 777-88-99",
                "type", "WHOLESALE",
                "message", "Хочу оптовые поставки");
        return restTemplate.exchange("/api/v1/site/requests", HttpMethod.POST,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    // ----- тесты ----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publicRequestCreatesLeadInUnassignedPoolAndOutboxEvent() {
        String manager = createStaffAndLogin(Role.MANAGER, "mgr-sr-" + UUID.randomUUID() + "@test.by");

        long leadsBefore = count("leads");
        ResponseEntity<ApiResponse> created = createRequest("idem-sr-" + UUID.randomUUID());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> request = (Map<String, Object>) created.getBody().getData();
        assertThat(request.get("status")).isEqualTo("NEW");

        // лид создан в пуле «не назначено» (2.7)
        assertThat(count("leads")).isEqualTo(leadsBefore + 1);
        ResponseEntity<ApiResponse> pool = exchange(manager, HttpMethod.GET,
                "/api/v1/crm/leads?scope=unassigned", null);
        List<Map<String, Object>> poolLeads = (List<Map<String, Object>>) pool.getBody().getData();
        assertThat(poolLeads).extracting(l -> l.get("name")).contains("Посетитель сайта");

        // событие в outbox; поллер доставляет → SENT
        String requestId = request.get("id").toString();
        outboxPoller.pollOnce();
        Integer pending = jdbcTemplate.queryForObject(
                "select count(*) from outbox_messages where type = 'SITE_REQUEST_CREATED' and status = 'PENDING'",
                Integer.class);
        assertThat(pending).isZero();

        // менеджер видит заявку в списке и ведёт статус NEW→IN_PROGRESS→CLOSED
        ResponseEntity<ApiResponse> list = exchange(manager, HttpMethod.GET,
                "/api/v1/crm/site-requests?status=NEW", null);
        assertThat((List<Map<String, Object>>) list.getBody().getData())
                .extracting(r -> r.get("id"))
                .contains(requestId);

        ResponseEntity<ApiResponse> inProgress = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/site-requests/" + requestId + "/status?status=IN_PROGRESS", null);
        assertThat(inProgress.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> closed = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/site-requests/" + requestId + "/status?status=CLOSED", null);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);

        // NEW → CLOSED сразу запрещён (2.8)
        ResponseEntity<ApiResponse> fresh = createRequest(null);
        String freshId = ((Map<String, Object>) fresh.getBody().getData()).get("id").toString();
        ResponseEntity<ApiResponse> invalid = exchange(manager, HttpMethod.POST,
                "/api/v1/crm/site-requests/" + freshId + "/status?status=CLOSED", null);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void idempotencyPreventsDuplicateRequests() {
        String key = "idem-sr-" + UUID.randomUUID();
        long requestsBefore = siteRequestRepository.count();
        long leadsBefore = count("leads");

        ResponseEntity<ApiResponse> first = createRequest(key);
        ResponseEntity<ApiResponse> second = createRequest(key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(siteRequestRepository.count()).isEqualTo(requestsBefore + 1);
        assertThat(count("leads")).isEqualTo(leadsBefore + 1);
    }

    @Test
    void pollerRetriesAndMarksFailedAfterLimit() {
        // канал FAIL_TEST_* «падает» (симуляция выключенного SMTP, приёмка S31):
        // сообщение не теряется, ретраи по backoff, после 5 попыток — FAILED
        jdbcTemplate.update("""
                insert into outbox_messages (aggregate_type, aggregate_id, type, payload, status)
                values ('test', gen_random_uuid(), 'FAIL_TEST_X', '{"x":1}'::jsonb, 'PENDING')
                """);
        String messageId = jdbcTemplate.queryForObject(
                "select id::text from outbox_messages where type = 'FAIL_TEST_X' limit 1", String.class);

        for (int expectedAttempt = 1; expectedAttempt <= OutboxPoller.MAX_ATTEMPTS; expectedAttempt++) {
            // «ждём» backoff: сдвигаем срок следующей попытки в прошлое
            jdbcTemplate.update(
                    "update outbox_messages set next_attempt_at = now() - interval '1 minute' where id = ?::uuid",
                    java.util.UUID.fromString(messageId));
            outboxPoller.pollOnce();

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "select status::text as status, attempts from outbox_messages where id = ?::uuid",
                    java.util.UUID.fromString(messageId));
            if (expectedAttempt < OutboxPoller.MAX_ATTEMPTS) {
                assertThat(row.get("status")).isEqualTo("PENDING");
                assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(expectedAttempt);
            } else {
                assertThat(row.get("status")).isEqualTo("FAILED");
                assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(OutboxPoller.MAX_ATTEMPTS);
            }
        }
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }
}
