package by.gusto.catalog;

import by.gusto.catalog.repository.ProductRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S19.1: витринные флаги ХИТ/НОВИНКА + шаг весового товара.
 * Демо-данные V13: steyk-ribay — ХИТ, kolbaski-dlya-zharki — НОВИНКА, фарш — шаг 0.5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ShowcaseFlagsIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private String loginAsAdmin() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@gustomeat.by", "password", "change-me"), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedFlagsAreExposedInPublicCatalog() {
        ResponseEntity<ApiResponse> response =
                restTemplate.getForEntity("/api/v1/catalog/products?size=50", ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().getData();
        Map<String, Map<String, Object>> bySku = items.stream()
                .collect(java.util.stream.Collectors.toMap(i -> (String) i.get("sku"), i -> i));

        assertThat(bySku.get("steyk-ribay").get("isHit")).isEqualTo(true);
        assertThat(bySku.get("kolbaski-dlya-zharki").get("isNew")).isEqualTo(true);
        assertThat(bySku.get("farsh-govyazhiy").get("weightStep")).isEqualTo(0.5);
        // товар без флагов и шага
        assertThat(bySku.get("yaytsa-kurinye-s0").get("isHit")).isEqualTo(false);
        assertThat(bySku.get("yaytsa-kurinye-s0").get("weightStep")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminTogglesShowcaseFlagsAndCatalogReflectsThem() {
        String token = loginAsAdmin();
        var product = productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay").orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        // снять ХИТ, поставить НОВИНКУ
        ResponseEntity<ApiResponse> patched = restTemplate.exchange(
                "/api/v1/admin/catalog/products/" + product.getId() + "/showcase",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("isHit", false, "isNew", true), headers),
                ApiResponse.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) patched.getBody().getData();
        assertThat(data.get("isHit")).isEqualTo(false);
        assertThat(data.get("isNew")).isEqualTo(true);

        ResponseEntity<ApiResponse> catalog =
                restTemplate.getForEntity("/api/v1/catalog/products?search=рибай", ApiResponse.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.getBody().getData();
        assertThat(items.get(0).get("isHit")).isEqualTo(false);
        assertThat(items.get(0).get("isNew")).isEqualTo(true);

        // возврат как было
        restTemplate.exchange("/api/v1/admin/catalog/products/" + product.getId() + "/showcase",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("isHit", true, "isNew", false), headers),
                ApiResponse.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void showcasePatchRequiresAdmin() {
        // физлицо не может править витрину
        String email = "shopper-" + java.util.UUID.randomUUID() + "@test.by";
        restTemplate.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "password123", "fullName", "Клиент Тест"), ApiResponse.class);
        ResponseEntity<ApiResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), ApiResponse.class);
        String token = (String) ((Map<String, Object>) login.getBody().getData()).get("accessToken");

        var product = productRepository.findBySkuAndDeletedAtIsNull("steyk-ribay").orElseThrow();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse> patched = restTemplate.exchange(
                "/api/v1/admin/catalog/products/" + product.getId() + "/showcase",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("isHit", true, "isNew", true), headers),
                ApiResponse.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
