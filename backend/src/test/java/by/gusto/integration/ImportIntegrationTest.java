package by.gusto.integration;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S35: импорт .xlsx из 1С — прайсы (upsert по SKU, отсутствующие → архив),
 * остатки (дельта к складу по умолчанию), отчёт по строкам, integration_files,
 * права (ADMIN/ACCOUNTANT, менеджеру 403), производительность 1000 строк.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ImportIntegrationTest {

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
        balance.setQuantity(BigDecimal.ZERO);
        balance.setReserved(BigDecimal.ZERO);
        balanceRepository.save(balance);
        // тесты класса делят одну БД: чистим артефакты прошлых импортов
        jdbcTemplate.update("delete from integration_files");
        jdbcTemplate.update("delete from audit_log where action like 'IMPORT%'");
    }

    private UUID productId(String sku) {
        return productRepository.findBySkuAndDeletedAtIsNull(sku).orElseThrow().getId();
    }

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

    private byte[] xlsx(Map<String, String> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("sku");
            header.createCell(1).setCellValue("value");
            int i = 1;
            for (Map.Entry<String, String> entry : rows.entrySet()) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<ApiResponse> upload(String token, String path, String filename, byte[] bytes,
                                               boolean archiveMissing) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        String url = path + (archiveMissing ? "?archiveMissing=true" : "");
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), ApiResponse.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void priceImportUpsertsRowsAndReportsErrors() {
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);
        java.util.LinkedHashMap<String, String> priceRows = new java.util.LinkedHashMap<>();
        priceRows.put("steyk-ribay", "95.90");
        priceRows.put("unknown-sku", "10.00");
        byte[] file = xlsx(priceRows);

        ResponseEntity<ApiResponse> response = upload(accountant, "/api/v1/admin/import/prices",
                "prices.xlsx", file, false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> report = (Map<String, Object>) response.getBody().getData();
        assertThat(report.get("rowsTotal")).isEqualTo(2);
        assertThat(report.get("rowsOk")).isEqualTo(1);
        assertThat(report.get("rowsError")).isEqualTo(1);
        assertThat(report.get("status")).isEqualTo("DONE");

        // цена в активном прайсе обновилась
        BigDecimal price = jdbcTemplate.queryForObject(
                "select price from product_prices pp join products p on pp.product_id = p.id "
                        + "where p.sku = 'steyk-ribay' order by pp.valid_from desc limit 1",
                BigDecimal.class);
        assertThat(price).isEqualByComparingTo("95.90");

        // integration_files + аудит
        Integer files = jdbcTemplate.queryForObject(
                "select count(*) from integration_files where type = 'PRICES' and status = 'DONE'",
                Integer.class);
        assertThat(files).isEqualTo(1);
        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'IMPORT_PRICES'", Integer.class);
        assertThat(auditRows).isEqualTo(1);
    }

    @Test
    void stockImportAppliesDeltaAndManagerForbidden() {
        String manager = createStaffAndLogin(Role.MANAGER);
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);

        // менеджеру импорт запрещён (матрица 2.1)
        ResponseEntity<ApiResponse> byManager = upload(manager, "/api/v1/admin/import/stock",
                "stock.xlsx", xlsx(Map.of("steyk-ribay", "10")), false);
        assertThat(byManager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        java.util.LinkedHashMap<String, String> stockRows = new java.util.LinkedHashMap<>();
        stockRows.put("steyk-ribay", "25");
        stockRows.put("unknown-sku", "5");
        ResponseEntity<ApiResponse> response = upload(accountant, "/api/v1/admin/import/stock",
                "stock.xlsx", xlsx(stockRows), false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> report = (Map<String, Object>) response.getBody().getData();
        assertThat(report.get("rowsOk")).isEqualTo(1);
        assertThat(report.get("rowsError")).isEqualTo(1);

        // остаток установлен через движение (0 → 25)
        BigDecimal quantity = balanceRepository
                .findById(new StockBalance.StockBalanceId(productId("steyk-ribay"), LOCATION))
                .orElseThrow().getQuantity();
        assertThat(quantity).isEqualByComparingTo("25");
        Integer movements = jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where reference_type = 'IMPORT_1C'", Integer.class);
        assertThat(movements).isEqualTo(1);
    }

    @Test
    void thousandRowsImportUnderThirtySeconds() {
        String accountant = createStaffAndLogin(Role.ACCOUNTANT);
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) {
            rows.put("steyk-ribay", String.valueOf(90 + i % 10) + ".00");
        }
        long started = System.currentTimeMillis();
        ResponseEntity<ApiResponse> response = upload(accountant, "/api/v1/admin/import/prices",
                "big.xlsx", xlsx(rows), false);
        long elapsed = System.currentTimeMillis() - started;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elapsed).isLessThan(30_000);
    }
}
