package by.gusto.admin;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S40: харденинг — security-заголовки, CORS-allowlist, rate limit
 * экспорта/импорта (10/час на пользователя), приватные файлы без токена.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityHardeningIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // лимиты живут в Redis между тестами
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    private String login(Role role) {
        String email = "sec-" + UUID.randomUUID() + "@test.by";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("staff-pass"))
                .fullName("Сотрудник " + role)
                .role(role)
                .active(true)
                .build());
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "staff-pass"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private ResponseEntity<String> exchangeRaw(String token, HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    private byte[] xlsx() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("sku");
            header.createCell(1).setCellValue("value");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("steyk-ribay");
            row.createCell(1).setCellValue("10");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> upload(String token, String type, byte[] bytes) {
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
                new HttpEntity<>(body, headers), String.class);
    }

    // ----- security-заголовки ------------------------------------------------------

    @Test
    void securityHeadersArePresentOnResponses() {
        ResponseEntity<String> response = exchangeRaw(null, HttpMethod.GET, "/healthz");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    // ----- CORS-allowlist ----------------------------------------------------------

    @Test
    void corsPreflightAllowedForAllowlistedOriginOnly() {
        HttpHeaders allow = new HttpHeaders();
        allow.setOrigin("http://localhost:5173");
        allow.setAccessControlRequestMethod(HttpMethod.GET);
        allow.setAccessControlRequestHeaders(List.of("Authorization"));
        ResponseEntity<String> allowed = restTemplate.exchange("/api/v1/catalog/products",
                HttpMethod.OPTIONS, new HttpEntity<>(allow), String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("http://localhost:5173");

        HttpHeaders deny = new HttpHeaders();
        deny.setOrigin("http://evil.example");
        deny.setAccessControlRequestMethod(HttpMethod.GET);
        ResponseEntity<String> rejected = restTemplate.exchange("/api/v1/catalog/products",
                HttpMethod.OPTIONS, new HttpEntity<>(deny), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- rate limit экспорта/импорта ----------------------------------------------

    @Test
    void exportIsLimitedToTenPerHourPerUser() {
        String token = login(Role.ADMIN);
        String path = "/api/v1/admin/export/orders?from=" + LocalDate.now().minusDays(7)
                + "&to=" + LocalDate.now();

        for (int i = 0; i < 10; i++) {
            HttpStatus status = HttpStatus.valueOf(exchangeRaw(token, HttpMethod.GET, path).getStatusCode().value());
            assertThat(status).as("выгрузка #" + (i + 1)).isEqualTo(HttpStatus.OK);
        }
        // 11-я за час — 429
        assertThat(exchangeRaw(token, HttpMethod.GET, path).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void importPreviewIsRateLimited() {
        String token = login(Role.ADMIN);
        byte[] file = xlsx();

        for (int i = 0; i < 30; i++) {
            assertThat(upload(token, "prices", file).getStatusCode())
                    .as("предпросмотр #" + (i + 1)).isEqualTo(HttpStatus.OK);
        }
        assertThat(upload(token, "prices", file).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ----- приватные файлы -----------------------------------------------------------

    @Test
    void privateFileIsNotDownloadableWithoutToken() {
        // админ загружает приватный файл
        String token = login(Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // PNG-заголовок: приложение принимает только изображения (application.yml)
        body.add("file", new ByteArrayResource(
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0}) {
            @Override
            public String getFilename() {
                return "private.png";
            }
        });
        body.add("visibility", "PRIVATE");
        ResponseEntity<String> created = restTemplate.postForEntity("/api/v1/files",
                new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // storageKey из ответа — случайный UUID (не предсказуемый путь)
        assertThat(created.getBody()).containsPattern("storageKey[^a-f0-9]*[a-f0-9]{8}-[a-f0-9]{4}-");

        String storageKey = jdbcTemplate.queryForObject(
                "select storage_key from files where original_name = 'private.png' "
                        + "order by created_at desc limit 1", String.class);

        // аноним: файл должен быть недоступен (401/403), а не отдан
        ResponseEntity<String> anonymous = exchangeRaw(null, HttpMethod.GET,
                "/api/v1/files/" + storageKey);
        assertThat(anonymous.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
}
