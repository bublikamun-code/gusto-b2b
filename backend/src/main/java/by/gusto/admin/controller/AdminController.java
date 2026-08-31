package by.gusto.admin.controller;

import by.gusto.admin.service.AdminCompanyService;
import by.gusto.admin.service.AdminUserService;
import by.gusto.auth.dto.CreateUserRequest;
import by.gusto.auth.dto.TemporaryPasswordResponse;
import by.gusto.auth.dto.UpdateUserRequest;
import by.gusto.auth.dto.UserResponse;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.dto.CreateCompanyRequest;
import by.gusto.company.dto.UpdateCompanyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminCompanyService adminCompanyService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.listUsers()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUser(id)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(adminUserService.createUser(request)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.updateUser(id, request)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<TemporaryPasswordResponse>> resetPassword(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.resetPassword(id)));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        return ResponseEntity.ok(ApiResponse.success(adminCompanyService.listCompanies()));
    }

    @GetMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminCompanyService.getCompany(id)));
    }

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(adminCompanyService.createCompany(request)));
    }

    @PutMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminCompanyService.updateCompany(id, request)));
    }

    @DeleteMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable UUID id) {
        adminCompanyService.deactivateCompany(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
