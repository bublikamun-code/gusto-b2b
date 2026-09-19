package by.gusto.cms;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S37: CMS — статья создаётся черновиком, публикуется и видна публично;
 * после архивирования страница исчезает. Права: CRUD только ADMIN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ArticleIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private String createStaffAndLogin(Role role) {
        String email = "staff-" + UUID.randomUUID() + "@test.by";
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

    @Test
    @SuppressWarnings("unchecked")
    void articleLifecycleDraftPublishedArchived() {
        String admin = createStaffAndLogin(Role.ADMIN);
        String slug = "about-" + UUID.randomUUID();

        // дубликат slug → 409
        ResponseEntity<ApiResponse> created = restTemplate.exchange("/api/v1/admin/cms/articles",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", slug, "title", "О нас", "body", "Мы — мясной гастроном."),
                        authHeaders(admin)), ApiResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = ((Map<String, Object>) created.getBody().getData()).get("id").toString();

        restTemplate.exchange("/api/v1/admin/cms/articles", HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", slug, "title", "Дубль", "body", "x"),
                        authHeaders(admin)), ApiResponse.class)
                .getStatusCode().equals(HttpStatus.CONFLICT);

        // черновик не виден публично
        ResponseEntity<String> draftPage =
                restTemplate.getForEntity("/api/v1/cms/pages/" + slug, String.class);
        assertThat(draftPage.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // публикация → страница видна
        ResponseEntity<ApiResponse> published = restTemplate.exchange(
                "/api/v1/admin/cms/articles/" + id + "/publish", HttpMethod.POST,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) published.getBody().getData()).get("status"))
                .isEqualTo("PUBLISHED");

        ResponseEntity<String> page = restTemplate.getForEntity("/api/v1/cms/pages/" + slug, String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Мы — мясной гастроном.");

        // правка контента отражается
        restTemplate.exchange("/api/v1/admin/cms/articles/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("body", "Обновлённый текст."), authHeaders(admin)),
                ApiResponse.class);
        assertThat(restTemplate.getForEntity("/api/v1/cms/pages/" + slug, String.class).getBody())
                .contains("Обновлённый текст.");

        // повторная публикация → 409
        ResponseEntity<ApiResponse> again = restTemplate.exchange(
                "/api/v1/admin/cms/articles/" + id + "/publish", HttpMethod.POST,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // архив → страница исчезает
        restTemplate.exchange("/api/v1/admin/cms/articles/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(admin)), ApiResponse.class);
        assertThat(restTemplate.getForEntity("/api/v1/cms/pages/" + slug, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // менеджер не управляет CMS (матрица 2.1)
        String manager = createStaffAndLogin(Role.MANAGER);
        ResponseEntity<ApiResponse> byManager = restTemplate.exchange("/api/v1/admin/cms/articles",
                HttpMethod.GET, new HttpEntity<>(authHeaders(manager)), ApiResponse.class);
        assertThat(byManager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
