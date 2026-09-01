package by.gusto.auth;

import by.gusto.auth.dto.LoginRequest;
import by.gusto.auth.dto.TotpVerifyRequest;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RbacAnd2faIntegrationTest {

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
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private by.gusto.auth.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private by.gusto.auth.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void adminCanListUsers_managerAndCustomerCannot() {
        createUser("admin-rbac@test.by", Role.ADMIN);
        User manager = createUser("manager-rbac@test.by", Role.MANAGER);
        User customer = createUser("customer-rbac@test.by", Role.CUSTOMER_LEGAL);

        String adminToken = login("admin-rbac@test.by");
        String managerToken = login("manager-rbac@test.by");
        String customerToken = login("customer-rbac@test.by");

        assertThat(get("/api/v1/admin/users", adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/admin/users", managerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/users", customerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerSeesOnlyAssignedCompanies_andCannotAccessOtherCompany() {
        User manager1 = createUser("manager1@test.by", Role.MANAGER);
        User manager2 = createUser("manager2@test.by", Role.MANAGER);

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

        String token1 = login("manager1@test.by");

        ResponseEntity<ApiResponse> list = get("/api/v1/manager/companies", token1);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) list.getBody().getData();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("name")).isEqualTo("Company 1");

        assertThat(get("/api/v1/manager/companies/" + company1.getId(), token1).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/manager/companies/" + company2.getId(), token1).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerCanAccessOwnCompany_only() {
        Company company = companyRepository.save(Company.builder()
                .name("Customer Company")
                .unp("333333333")
                .build());
        User customer = createUserWithCompany("customer-company@test.by", Role.CUSTOMER_LEGAL, company.getId());
        Company other = companyRepository.save(Company.builder()
                .name("Other Company")
                .unp("444444444")
                .build());

        String token = login("customer-company@test.by");

        ResponseEntity<ApiResponse> own = get("/api/v1/cabinet/company", token);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) own.getBody().getData();
        assertThat(data.get("id")).isEqualTo(company.getId().toString());

        assertThat(get("/api/v1/manager/companies/" + other.getId(), token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerWithoutCompanyGets404() {
        createUser("customer-no-company@test.by", Role.CUSTOMER_LEGAL);
        String token = login("customer-no-company@test.by");
        assertThat(get("/api/v1/cabinet/company", token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void totpFlow_setupVerifyLoginWithCode_andRecoveryCode() throws Exception {
        User admin = createUser("admin-2fa@test.by", Role.ADMIN);
        String token = login("admin-2fa@test.by");

        ResponseEntity<ApiResponse> setup = post("/api/v1/auth/2fa/enable", token, null);
        assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> setupData = (Map<String, Object>) setup.getBody().getData();
        String secret = (String) setupData.get("secret");
        assertThat(secret).isNotBlank();

        String code = String.format("%06d", TimeBasedOneTimePasswordUtil.generateCurrentNumber(secret));
        ResponseEntity<ApiResponse> verify = post("/api/v1/auth/2fa/verify", token,
                new TotpVerifyRequest(code));
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> recovery = (Map<String, Object>) verify.getBody().getData();
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) recovery.get("recoveryCodes");
        assertThat(codes).hasSize(8);

        // login без кода → 2FA required
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin-2fa@test.by");
        loginRequest.setPassword("password123");
        ResponseEntity<ApiResponse> noCode = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, ApiResponse.class);
        assertThat(noCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noCode.getBody().getError().getCode()).isEqualTo("AUTH_2FA_REQUIRED");

        // login с неверным кодом
        loginRequest.setTotpCode("000000");
        ResponseEntity<ApiResponse> badCode = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, ApiResponse.class);
        assertThat(badCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(badCode.getBody().getError().getCode()).isEqualTo("AUTH_2FA_INVALID");

        // login с recovery-кодом
        loginRequest.setTotpCode(codes.get(0));
        ResponseEntity<ApiResponse> withRecovery = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, ApiResponse.class);
        assertThat(withRecovery.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withRecovery.getBody().getData()).isNotNull();

        // повтор тот же recovery-код уже не работает
        loginRequest.setTotpCode(codes.get(0));
        ResponseEntity<ApiResponse> secondRecovery = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, ApiResponse.class);
        assertThat(secondRecovery.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private User createUserWithCompany(String email, Role role, UUID companyId) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Test User")
                .role(role)
                .companyId(companyId)
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
}
