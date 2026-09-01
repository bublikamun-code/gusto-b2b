package by.gusto.file;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.catalog.dto.CategoryRequest;
import by.gusto.catalog.dto.ProductRequest;
import by.gusto.catalog.entity.Category;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.repository.ProductImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FileIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection("redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

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
    private FileRepository fileRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private by.gusto.auth.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private UUID productId;

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            0x3A, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, 0x5B, 0x63, (byte) 0xF8, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x00, 0x05, (byte) 0x18, (byte) 0xD8, 0x4E,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        productImageRepository.deleteAll();
        fileRepository.deleteAll();
        productPriceRepository.deleteAll();
        priceListRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createAdminAndLogin();
        productId = createProduct();
    }

    @Test
    void adminCanUploadPublicImageAndAnonymousCanDownloadIt() {
        ResponseEntity<ApiResponse> uploadResp = uploadFile("test.png", PNG_BYTES, "PUBLIC", adminToken);
        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) uploadResp.getBody().getData();
        String storageKey = (String) data.get("storageKey");
        String url = (String) data.get("url");
        assertThat(storageKey).isNotBlank();
        assertThat(url).contains("/api/v1/files/" + storageKey);
        assertThat(data.get("mimeType")).isEqualTo("image/png");

        ResponseEntity<byte[]> downloadResp = restTemplate.getForEntity(url, byte[].class);
        assertThat(downloadResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloadResp.getHeaders().getContentType().toString()).startsWith("image/png");
        assertThat(downloadResp.getBody()).isEqualTo(PNG_BYTES);
    }

    @Test
    void privateFileIsNotAccessibleToAnonymous() {
        ResponseEntity<ApiResponse> uploadResp = uploadFile("private.png", PNG_BYTES, "PRIVATE", adminToken);
        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        String storageKey = (String) ((Map<String, Object>) uploadResp.getBody().getData()).get("storageKey");

        ResponseEntity<ApiResponse> downloadResp = restTemplate.getForEntity(
                "/api/v1/files/" + storageKey, ApiResponse.class);
        assertThat(downloadResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanAttachImageToProductAndCatalogReturnsIt() {
        ResponseEntity<ApiResponse> uploadResp = uploadFile("product.png", PNG_BYTES, "PUBLIC", adminToken);
        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        String fileId = (String) ((Map<String, Object>) uploadResp.getBody().getData()).get("id");

        ResponseEntity<ApiResponse> attachResp = post(
                "/api/v1/admin/catalog/products/" + productId + "/images?fileId=" + fileId + "&sort=0",
                adminToken, null);
        assertThat(attachResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiResponse> catalogResp = restTemplate.getForEntity(
                "/api/v1/catalog/products/FILE-001", ApiResponse.class);
        assertThat(catalogResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> productData = (Map<String, Object>) catalogResp.getBody().getData();
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) productData.get("imageUrls");
        assertThat(imageUrls).hasSize(1);
        assertThat(imageUrls.get(0)).contains("/api/v1/files/");
    }

    @Test
    void invalidMimeTypeIsRejected() {
        byte[] text = "not an image".getBytes();
        ResponseEntity<ApiResponse> uploadResp = uploadFile("fake.png", text, "PUBLIC", adminToken);
        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String createAdminAndLogin() {
        userRepository.save(User.builder()
                .email("admin-files@test.by")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Admin Files")
                .role(Role.ADMIN)
                .active(true)
                .build());
        LoginRequest request = new LoginRequest();
        request.setEmail("admin-files@test.by");
        request.setPassword("password123");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        return (String) data.get("accessToken");
    }

    private UUID createProduct() {
        CategoryRequest root = CategoryRequest.builder().name("Мясо").slug("myaso").sort(1).build();
        ResponseEntity<ApiResponse> rootResp = post("/api/v1/admin/catalog/categories", adminToken, root);
        assertThat(rootResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        UUID categoryId = UUID.fromString((String) ((Map<String, Object>) rootResp.getBody().getData()).get("id"));

        ProductRequest productRequest = ProductRequest.builder()
                .sku("FILE-001")
                .name("Колбаса для фото")
                .categoryId(categoryId)
                .unit("кг")
                .build();
        ResponseEntity<ApiResponse> productResp = post("/api/v1/admin/catalog/products", adminToken, productRequest);
        assertThat(productResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) productResp.getBody().getData();
        return UUID.fromString((String) data.get("id"));
    }

    private ResponseEntity<ApiResponse> uploadFile(String filename, byte[] content, String visibility, String token) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("visibility", visibility);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange("/api/v1/files", HttpMethod.POST,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }
}
