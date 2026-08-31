package by.gusto.admin;

import by.gusto.auth.dto.CreateUserRequest;
import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.dto.UpdateUserRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.RefreshTokenRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.dto.CreateCompanyRequest;
import by.gusto.company.dto.UpdateCompanyRequest;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminCrudIntegrationTest {

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
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanCrudUser() {
        createUser("admin-crud@test.by", Role.ADMIN);
        String token = login("admin-crud@test.by");

        // create
        CreateUserRequest create = CreateUserRequest.builder()
                .email("manager-crud@test.by")
                .fullName("Manager CRUD")
                .phone("+375291111111")
                .role(Role.MANAGER)
                .build();
        ResponseEntity<ApiResponse> created = post("/api/v1/admin/users", token, create);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdData = (Map<String, Object>) created.getBody().getData();
        UUID userId = UUID.fromString((String) createdData.get("id"));
        assertThat(createdData.get("email")).isEqualTo("manager-crud@test.by");

        // get
        ResponseEntity<ApiResponse> got = get("/api/v1/admin/users/" + userId, token);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);

        // update
        UpdateUserRequest update = UpdateUserRequest.builder()
                .fullName("Updated Manager")
                .active(false)
                .build();
        ResponseEntity<ApiResponse> updated = put("/api/v1/admin/users/" + userId, token, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedData = (Map<String, Object>) updated.getBody().getData();
        assertThat(updatedData.get("fullName")).isEqualTo("Updated Manager");
        assertThat(updatedData.get("isActive")).isEqualTo(false);

        // delete
        ResponseEntity<ApiResponse> deleted = delete("/api/v1/admin/users/" + userId, token);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        // get after delete → 404
        ResponseEntity<ApiResponse> afterDelete = get("/api/v1/admin/users/" + userId, token);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCannotCreateUserWithDuplicateEmail() {
        createUser("admin-dup@test.by", Role.ADMIN);
        createUser("existing@test.by", Role.MANAGER);
        String token = login("admin-dup@test.by");

        CreateUserRequest create = CreateUserRequest.builder()
                .email("existing@test.by")
                .fullName("Duplicate")
                .role(Role.MANAGER)
                .build();
        ResponseEntity<ApiResponse> response = post("/api/v1/admin/users", token, create);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminCanCrudCompany() {
        createUser("admin-company@test.by", Role.ADMIN);
        String token = login("admin-company@test.by");

        // create
        CreateCompanyRequest create = CreateCompanyRequest.builder()
                .name("ООО Густо")
                .shortName("Густо")
                .unp("123456789")
                .legalAddress("Минск")
                .contactPhone("+375292222222")
                .contactEmail("info@gusto.by")
                .build();
        ResponseEntity<ApiResponse> created = post("/api/v1/admin/companies", token, create);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdData = (Map<String, Object>) created.getBody().getData();
        UUID companyId = UUID.fromString((String) createdData.get("id"));
        assertThat(createdData.get("name")).isEqualTo("ООО Густо");

        // get
        ResponseEntity<ApiResponse> got = get("/api/v1/admin/companies/" + companyId, token);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);

        // update
        UpdateCompanyRequest update = UpdateCompanyRequest.builder()
                .name("ООО Густо Плюс")
                .contactEmail("new@gusto.by")
                .build();
        ResponseEntity<ApiResponse> updated = put("/api/v1/admin/companies/" + companyId, token, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedData = (Map<String, Object>) updated.getBody().getData();
        assertThat(updatedData.get("name")).isEqualTo("ООО Густо Плюс");
        assertThat(updatedData.get("contactEmail")).isEqualTo("new@gusto.by");

        // deactivate
        ResponseEntity<ApiResponse> deactivated = delete("/api/v1/admin/companies/" + companyId, token);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ApiResponse> after = get("/api/v1/admin/companies/" + companyId, token);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterData = (Map<String, Object>) after.getBody().getData();
        assertThat(afterData.get("status")).isEqualTo("INACTIVE");
    }

    @Test
    void companyUnpValidationAndDuplicate() {
        createUser("admin-unp@test.by", Role.ADMIN);
        String token = login("admin-unp@test.by");

        // invalid format
        CreateCompanyRequest invalid = CreateCompanyRequest.builder()
                .name("Invalid UNP")
                .unp("12345")
                .build();
        ResponseEntity<ApiResponse> invalidResponse = post("/api/v1/admin/companies", token, invalid);
        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // valid 10-digit UNP
        CreateCompanyRequest first = CreateCompanyRequest.builder()
                .name("First IP")
                .unp("1234567890")
                .build();
        ResponseEntity<ApiResponse> firstResponse = post("/api/v1/admin/companies", token, first);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // duplicate UNP
        CreateCompanyRequest duplicate = CreateCompanyRequest.builder()
                .name("Duplicate UNP")
                .unp("1234567890")
                .build();
        ResponseEntity<ApiResponse> duplicateResponse = post("/api/v1/admin/companies", token, duplicate);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminResetPasswordAllowsLogin() {
        User user = createUser("user-reset@test.by", Role.MANAGER);
        createUser("admin-reset@test.by", Role.ADMIN);
        String token = login("admin-reset@test.by");

        ResponseEntity<ApiResponse> response = post(
                "/api/v1/admin/users/" + user.getId() + "/reset-password", token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        String temporaryPassword = (String) data.get("temporaryPassword");
        assertThat(temporaryPassword).isNotBlank();

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user-reset@test.by");
        loginRequest.setPassword(temporaryPassword);
        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, ApiResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void managerCanUpdateAssignedCompany_andCannotUpdateOther() {
        User manager1 = createUser("manager1-crud@test.by", Role.MANAGER);
        User manager2 = createUser("manager2-crud@test.by", Role.MANAGER);

        Company company1 = companyRepository.save(Company.builder()
                .name("Company 1")
                .unp("111111111")
                .managerId(manager1.getId())
                .build());
        Company company2 = companyRepository.save(Company.builder()
                .name("Company 2")
                .unp("222222222")
                .managerId(manager2.getId())
                .build());

        String token1 = login("manager1-crud@test.by");

        UpdateCompanyRequest update = UpdateCompanyRequest.builder()
                .contactEmail("manager1@company.by")
                .build();
        ResponseEntity<ApiResponse> own = put(
                "/api/v1/manager/companies/" + company1.getId(), token1, update);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse> other = put(
                "/api/v1/manager/companies/" + company2.getId(), token1, update);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Test User")
                .role(role)
                .active(true)
                .build());
    }

    private String login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("password123");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ApiResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        return (String) data.get("accessToken");
    }

    private ResponseEntity<ApiResponse> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), ApiResponse.class);
    }

    private ResponseEntity<ApiResponse> put(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
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
