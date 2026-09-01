package by.gusto.cabinet;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.entity.Brand;
import by.gusto.catalog.entity.Category;
import by.gusto.catalog.entity.CustomerDiscount;
import by.gusto.catalog.entity.PriceList;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.entity.ProductPrice;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.CustomerDiscountRepository;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CabinetCatalogIntegrationTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceListRepository priceListRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private CustomerDiscountRepository customerDiscountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private by.gusto.auth.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        customerDiscountRepository.deleteAll();
        productPriceRepository.deleteAll();
        priceListRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void cabinetCatalogReturnsCustomerPriceWithDiscount() {
        createCustomerAndLogin();

        Category category = categoryRepository.save(Category.builder().name("Мясо").slug("myaso").active(true).build());
        Brand brand = brandRepository.save(Brand.builder().name("Густо").slug("gusto").build());
        Product product = productRepository.save(Product.builder()
                .sku("SKU-CABINET")
                .name("Стейк рибай")
                .categoryId(category.getId())
                .brandId(brand.getId())
                .unit("кг")
                .active(true)
                .build());

        PriceList priceList = priceListRepository.save(PriceList.builder()
                .name("Розница")
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .active(true)
                .build());
        productPriceRepository.save(ProductPrice.builder()
                .priceListId(priceList.getId())
                .productId(product.getId())
                .price(BigDecimal.valueOf(100.00))
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .build());

        customerDiscountRepository.save(CustomerDiscount.builder()
                .companyId(companyId)
                .brandId(brand.getId())
                .discountPercent(BigDecimal.valueOf(15.00))
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .build());

        ResponseEntity<ApiResponse> response = get("/api/v1/cabinet/catalog?search=стейк", customerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().getData();
        assertThat(data).hasSize(1);

        Map<String, Object> item = data.get(0);
        assertThat(item.get("retailPrice")).isEqualTo(100.00);
        assertThat(item.get("customerPrice")).isEqualTo(85.00);
        assertThat(item.get("sku")).isEqualTo("SKU-CABINET");
    }

    private void createCustomerAndLogin() {
        Company company = companyRepository.save(Company.builder().name("ООО Тест").status("ACTIVE").build());
        companyId = company.getId();

        userRepository.save(User.builder()
                .email("customer@test.by")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Customer")
                .role(Role.CUSTOMER_LEGAL)
                .companyId(companyId)
                .active(true)
                .build());

        LoginRequest request = new LoginRequest();
        request.setEmail("customer@test.by");
        request.setPassword("password123");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        customerToken = (String) data.get("accessToken");
    }

    private ResponseEntity<ApiResponse> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), ApiResponse.class);
    }
}
