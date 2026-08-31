package by.gusto.auth;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.dto.PasswordResetConfirmRequest;
import by.gusto.auth.dto.PasswordResetRequest;
import by.gusto.auth.dto.RegisterRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.repository.PasswordResetTokenRepository;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.auth.service.PasswordResetService;
import by.gusto.common.api.ApiResponse;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void loginWithValidCredentialsReturnsAccessTokenAndRefreshCookie() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@gustomeat.by");
        request.setPassword("change-me");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data).containsKey("accessToken");
        assertThat(data.get("tokenType")).isEqualTo("Bearer");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.contains("refresh_token"));
    }

    @Test
    void loginWithInvalidCredentialsReturns401() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@gustomeat.by");
        request.setPassword("wrong-password");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError().getCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void registerCreatesIndividualCustomer() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("individual@test.by");
        request.setPassword("password123");
        request.setFullName("Иван Иванов");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data.get("email")).isEqualTo("individual@test.by");
        assertThat(data.get("role")).isEqualTo("CUSTOMER_INDIVIDUAL");
        assertThat(userRepository.findByEmailIgnoreCase("individual@test.by")).isPresent();
    }

    @Test
    void refreshReturnsNewAccessTokenAndRotatesRefreshToken() {
        String rawRefresh = loginAndExtractRefreshCookie();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + rawRefresh);
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data).containsKey("accessToken");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.contains("refresh_token"));

        // старый токен ревокнут
        assertThat(refreshTokenRepository.findByTokenHash(sha256(rawRefresh)))
                .isPresent().get().satisfies(t -> assertThat(t.getRevoked()).isTrue());
    }

    @Test
    void reusedRefreshTokenRevokesFamilyAndReturns401() {
        String rawRefresh = loginAndExtractRefreshCookie();

        // первый refresh
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + rawRefresh);
        restTemplate.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), ApiResponse.class);

        // повтор старого токена
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError().getCode()).isEqualTo("AUTH_REFRESH_REUSED");
    }

    @Test
    void logoutRevokesRefreshToken() {
        String rawRefresh = loginAndExtractRefreshCookie();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + rawRefresh);
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshTokenRepository.findByTokenHash(sha256(rawRefresh)))
                .isPresent().get().satisfies(t -> assertThat(t.getRevoked()).isTrue());
    }

    @Test
    void loginRateLimitBlocksAfterFiveAttempts() {
        LoginRequest request = new LoginRequest();
        request.setEmail("rate-limit@test.by");
        request.setPassword("wrong");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<ApiResponse> r = restTemplate.postForEntity(
                    "/api/v1/auth/login", request, ApiResponse.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ApiResponse> blocked = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().getError().getCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void passwordResetFlowWorksWithHashedToken() {
        RegisterRequest register = new RegisterRequest();
        register.setEmail("reset@test.by");
        register.setPassword("password123");
        register.setFullName("Сброс Пароля");
        restTemplate.postForEntity("/api/v1/auth/register", register, ApiResponse.class);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail("reset@test.by");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/password-reset/request", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Получаем raw-токен через сервис (в проде он приходит по email)
        String rawToken = passwordResetService.createToken("reset@test.by");
        assertThat(rawToken).isNotBlank();

        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest();
        confirm.setToken(rawToken);
        confirm.setNewPassword("newpassword123");
        ResponseEntity<ApiResponse> confirmResponse = restTemplate.postForEntity(
                "/api/v1/auth/password-reset/confirm", confirm, ApiResponse.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Повторное использование того же токена — отказ
        ResponseEntity<ApiResponse> second = restTemplate.postForEntity(
                "/api/v1/auth/password-reset/confirm", confirm, ApiResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String loginAndExtractRefreshCookie() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@gustomeat.by");
        request.setPassword("change-me");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);

        String cookie = Objects.requireNonNull(response.getHeaders().get(HttpHeaders.SET_COOKIE)).stream()
                .filter(c -> c.contains("refresh_token="))
                .findFirst()
                .orElseThrow();
        return cookie.split(";")[0].substring("refresh_token=".length());
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
