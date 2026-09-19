package by.gusto.integration;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.inventory.entity.StockBalance;
import by.gusto.inventory.repository.StockBalanceRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S36: экспорт для 1С — заказы/счета/накладные за период (.xlsx), прайс клиента
 * со своими ценами; integration_files (EXPORT) фиксирует выгрузки.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ExportIntegrationTest {

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
    private CompanyRepository companyRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockBalanceRepository balanceRepository;

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

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

    private String login(String email, String password) {
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Map<String, Object>) response.getBody().getData()).get("accessToken").toString();
    }

    private Company createCompanyWithClient(String name, String unp) {
        Company company = companyRepository.save(Company.builder()
                .name(name).unp(unp).legalAddress("г. Минск, ул. Тестовая, 1").build());
        userRepository.save(User.builder()
                .email("legal-" + UUID.randomUUID() + "@test.by")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Клиент Юрлицо")
                .role(Role.CUSTOMER_LEGAL)
                .companyId(company.getId())
                .active(true)
                .emailConfirmedAt(java.time.Instant.now())
                .build());
        return company;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<byte[]> download(String token, String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), byte[].class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderExportContainsCreatedOrder() {
        String admin = login("admin@gustomeat.by", "change-me");
        Company company = createCompanyWithClient("ООО «Экспорт»", "192953730");

        restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "customerCompanyId", company.getId().toString(),
                        "deliveryType", "PICKUP",
                        "items", java.util.List.of(Map.of(
                                "productId", productId("steyk-ribay").toString(),
                                "quantity", 2))), authHeaders(admin)), ApiResponse.class);

        ResponseEntity<byte[]> xlsx = download(admin,
                "/api/v1/admin/export/orders?from=2026-01-01&to=2026-12-31");
        assertThat(xlsx.getStatusCode()).isEqualTo(HttpStatus.OK);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getBody()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Номер");
            boolean found = false;
            for (Row row : sheet) {
                String number = row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue();
                if (number.startsWith("З-2026-")) {
                    found = true;
                    assertThat(row.getCell(4).getStringCellValue()).isEqualTo("ООО «Экспорт»");
                }
            }
            assertThat(found).isTrue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        // выгрузка зафиксирована в integration_files (EXPORT)
        Integer exports = jdbcTemplate.queryForObject(
                "select count(*) from integration_files where direction = 'EXPORT' and type = 'ORDERS'",
                Integer.class);
        assertThat(exports).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientPricingExportUsesPersonalPrice() {
        Company company = createCompanyWithClient("ООО «Прайс»", "192953731");
        String legalEmail = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER_LEGAL && company.getId().equals(u.getCompanyId()))
                .map(u -> u.getEmail()).findFirst().orElseThrow();
        String legal = login(legalEmail, "password123");

        // персональная цена 50.00 на стейк
        jdbcTemplate.update("""
                insert into customer_prices (id, company_id, product_id, price, valid_from)
                values (gen_random_uuid(), ?, ?, 50.00, current_date)
                """, company.getId(), productId("steyk-ribay"));

        ResponseEntity<byte[]> xlsx = download(legal, "/api/v1/cabinet/pricing/export");
        assertThat(xlsx.getStatusCode()).isEqualTo(HttpStatus.OK);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getBody()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Артикул");
            boolean found = false;
            for (Row row : sheet) {
                String sku = row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue();
                if (sku.equals("steyk-ribay")) {
                    found = true;
                    assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(50.0);
                }
            }
            assertThat(found).isTrue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
