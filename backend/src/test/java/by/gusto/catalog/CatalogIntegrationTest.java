package by.gusto.catalog;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.dto.BrandRequest;
import by.gusto.catalog.dto.CategoryRequest;
import by.gusto.catalog.dto.ProductRequest;
import by.gusto.catalog.entity.Brand;
import by.gusto.catalog.entity.Category;
import by.gusto.catalog.entity.PriceList;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.entity.ProductPrice;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
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
class CatalogIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private by.gusto.auth.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        productPriceRepository.deleteAll();
        priceListRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanCrudCategoryAndBuildTree() {
        createAdminAndLogin();

        // create root
        CategoryRequest root = CategoryRequest.builder().name("Мясо").slug("myaso").sort(1).build();
        ResponseEntity<ApiResponse> rootResp = post("/api/v1/admin/catalog/categories", adminToken, root);
        assertThat(rootResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        UUID rootId = UUID.fromString((String) ((Map<String, Object>) rootResp.getBody().getData()).get("id"));

        // create child
        CategoryRequest child = CategoryRequest.builder().name("Колбасы").slug("kolbasy").parentId(rootId).sort(1).build();
        ResponseEntity<ApiResponse> childResp = post("/api/v1/admin/catalog/categories", adminToken, child);
        assertThat(childResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // tree
        ResponseEntity<ApiResponse> tree = get("/api/v1/admin/catalog/categories/tree", adminToken);
        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> treeData = (List<Map<String, Object>>) tree.getBody().getData();
        assertThat(treeData).hasSize(1);
        assertThat(treeData.get(0).get("name")).isEqualTo("Мясо");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) treeData.get(0).get("children");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).get("name")).isEqualTo("Колбасы");

        // update
        CategoryRequest update = CategoryRequest.builder().name("Мясная продукция").slug("myasnaya-produktsiya").build();
        ResponseEntity<ApiResponse> updated = put("/api/v1/admin/catalog/categories/" + rootId, adminToken, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedData = (Map<String, Object>) updated.getBody().getData();
        assertThat(updatedData.get("name")).isEqualTo("Мясная продукция");

        // delete
        assertThat(delete("/api/v1/admin/catalog/categories/" + rootId, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        UUID childId = UUID.fromString((String) ((Map<String, Object>) childResp.getBody().getData()).get("id"));
        assertThat(delete("/api/v1/admin/catalog/categories/" + childId, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(delete("/api/v1/admin/catalog/categories/" + rootId, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanCrudBrand() {
        createAdminAndLogin();

        BrandRequest request = BrandRequest.builder().name("Густо").slug("gusto").build();
        ResponseEntity<ApiResponse> created = post("/api/v1/admin/catalog/brands", adminToken, request);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) created.getBody().getData();
        assertThat(data.get("name")).isEqualTo("Густо");
        UUID id = UUID.fromString((String) data.get("id"));

        BrandRequest update = BrandRequest.builder().name("Густо Премиум").slug("gusto-premium").build();
        ResponseEntity<ApiResponse> updated = put("/api/v1/admin/catalog/brands/" + id, adminToken, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) updated.getBody().getData()).get("name")).isEqualTo("Густо Премиум");

        assertThat(delete("/api/v1/admin/catalog/brands/" + id, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanCrudProductAndSoftDelete() {
        createAdminAndLogin();
        Category category = categoryRepository.save(Category.builder().name("Мясо").slug("myaso").build());
        Brand brand = brandRepository.save(Brand.builder().name("Густо").slug("gusto").build());

        ProductRequest request = ProductRequest.builder()
                .sku("SKU-001")
                .name("Колбаса докторская")
                .categoryId(category.getId())
                .brandId(brand.getId())
                .unit("кг")
                .build();
        ResponseEntity<ApiResponse> created = post("/api/v1/admin/catalog/products", adminToken, request);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) created.getBody().getData();
        UUID id = UUID.fromString((String) data.get("id"));
        assertThat(data.get("sku")).isEqualTo("SKU-001");

        ResponseEntity<ApiResponse> got = get("/api/v1/admin/catalog/products/" + id, adminToken);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProductRequest update = ProductRequest.builder().name("Колбаса докторская премиум").sku("SKU-001").categoryId(category.getId()).build();
        ResponseEntity<ApiResponse> updated = put("/api/v1/admin/catalog/products/" + id, adminToken, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) updated.getBody().getData()).get("name"))
                .isEqualTo("Колбаса докторская премиум");

        assertThat(delete("/api/v1/admin/catalog/products/" + id, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> afterDelete = get("/api/v1/admin/catalog/products/" + id, adminToken);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicCatalogSearchFiltersAndRetailPrice() {
        createAdminAndLogin();
        Category category = categoryRepository.save(Category.builder().name("Мясо").slug("myaso").active(true).build());
        Brand brand = brandRepository.save(Brand.builder().name("Густо").slug("gusto").build());
        Product product = productRepository.save(Product.builder()
                .sku("SKU-SEARCH")
                .name("Докторская колбаса")
                .categoryId(category.getId())
                .brandId(brand.getId())
                .unit("кг")
                .active(true)
                .build());

        PriceList priceList = priceListRepository.save(PriceList.builder()
                .name("Базовый")
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .active(true)
                .build());
        productPriceRepository.save(ProductPrice.builder()
                .priceListId(priceList.getId())
                .productId(product.getId())
                .price(BigDecimal.valueOf(25.50))
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusDays(30))
                .build());

        // search by partial word
        ResponseEntity<ApiResponse> search = get("/api/v1/catalog/products?search=доктор", null);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> searchData = (List<Map<String, Object>>) search.getBody().getData();
        assertThat(searchData).hasSize(1);
        assertThat(searchData.get(0).get("retailPrice")).isEqualTo(25.50);

        // filter by category
        ResponseEntity<ApiResponse> byCategory = get("/api/v1/catalog/products?categoryId=" + category.getId(), null);
        assertThat(byCategory.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> catData = (List<Map<String, Object>>) byCategory.getBody().getData();
        assertThat(catData).hasSize(1);

        // filter by brand
        ResponseEntity<ApiResponse> byBrand = get("/api/v1/catalog/products?brandId=" + brand.getId(), null);
        assertThat(byBrand.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brandData = (List<Map<String, Object>>) byBrand.getBody().getData();
        assertThat(brandData).hasSize(1);

        // product by SKU
        ResponseEntity<ApiResponse> bySku = get("/api/v1/catalog/products/SKU-SEARCH", null);
        assertThat(bySku.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> skuData = (Map<String, Object>) bySku.getBody().getData();
        assertThat(skuData.get("sku")).isEqualTo("SKU-SEARCH");
        assertThat(skuData.get("retailPrice")).isEqualTo(25.50);
    }

    @Test
    void publicCatalogReturnsCategoriesAndBrands() {
        createAdminAndLogin();
        categoryRepository.save(Category.builder().name("Мясо").slug("myaso").active(true).sort(1).build());
        brandRepository.save(Brand.builder().name("Густо").slug("gusto").build());

        ResponseEntity<ApiResponse> categories = get("/api/v1/catalog/categories", null);
        assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> catData = (List<Map<String, Object>>) categories.getBody().getData();
        assertThat(catData).hasSize(1);

        ResponseEntity<ApiResponse> brands = get("/api/v1/catalog/brands", null);
        assertThat(brands.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brandData = (List<Map<String, Object>>) brands.getBody().getData();
        assertThat(brandData).hasSize(1);
    }

    private String adminToken;

    private void createAdminAndLogin() {
        userRepository.save(User.builder()
                .email("admin-catalog@test.by")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Admin")
                .role(Role.ADMIN)
                .active(true)
                .build());
        LoginRequest request = new LoginRequest();
        request.setEmail("admin-catalog@test.by");
        request.setPassword("password123");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        adminToken = (String) data.get("accessToken");
    }

    private ResponseEntity<ApiResponse> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> put(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> delete(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiResponse.class);
    }
}
