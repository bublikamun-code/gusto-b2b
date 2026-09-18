package by.gusto.cabinet;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S23.1: профиль и смена пароля в кабинете.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProfileIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private String registerAndLogin() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        String email = "client-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Клиент Тест"), ApiResponse.class);
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        return (String) ((Map<String, Object>) login.getBody().getData()).get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAndUpdateProfile() {
        String token = registerAndLogin();

        ResponseEntity<ApiResponse> profile = restTemplate.exchange(
                "/api/v1/cabinet/profile", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), ApiResponse.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) profile.getBody().getData();
        assertThat(data.get("fullName")).isEqualTo("Клиент Тест");
        assertThat(data.get("role")).isEqualTo("CUSTOMER_INDIVIDUAL");

        ResponseEntity<ApiResponse> updated = restTemplate.exchange(
                "/api/v1/cabinet/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("fullName", "Иван Новый", "phone", "+375 29 555-66-77"),
                        authHeaders(token)),
                ApiResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) updated.getBody().getData()).get("fullName")).isEqualTo("Иван Новый");

        ResponseEntity<ApiResponse> reread = restTemplate.exchange(
                "/api/v1/cabinet/profile", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), ApiResponse.class);
        assertThat(((Map<String, Object>) reread.getBody().getData()).get("phone")).isEqualTo("+375 29 555-66-77");
    }

    @Test
    @SuppressWarnings("unchecked")
    void changePasswordVerifiesCurrentAndInvalidatesOldPassword() {
        String token = registerAndLogin();

        // неверный текущий пароль → 401
        ResponseEntity<ApiResponse> wrong = restTemplate.exchange(
                "/api/v1/cabinet/profile/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "wrong-password-1", "newPassword", "new-password-9"),
                        authHeaders(token)),
                ApiResponse.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody().getError().getCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");

        // верная смена
        ResponseEntity<ApiResponse> changed = restTemplate.exchange(
                "/api/v1/cabinet/profile/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "password123", "newPassword", "new-password-9"),
                        authHeaders(token)),
                ApiResponse.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);

        // новый аккаунт для проверки входа: после смены старый пароль не пускает, новый — пускает
        String email = "client-" + UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Второй Тест"), ApiResponse.class);
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        String token2 = (String) ((Map<String, Object>) login.getBody().getData()).get("accessToken");

        restTemplate.exchange("/api/v1/cabinet/profile/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "password123", "newPassword", "new-password-9"),
                        authHeaders(token2)),
                ApiResponse.class);

        ResponseEntity<ApiResponse> loginOld = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        assertThat(loginOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ApiResponse> loginNew = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "new-password-9"), ApiResponse.class);
        assertThat(loginNew.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
