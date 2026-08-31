package by.gusto.manager.controller;

import by.gusto.auth.security.RequireOwnership;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerController {

    private final AuthContext authContext;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        return ResponseEntity.ok(ApiResponse.success(
                companyMapper.toResponseList(companyRepository.findAllByManagerId(authContext.getCurrentUser().getId()))));
    }

    @GetMapping("/companies/{id}")
    @RequireOwnership(resource = "company", idParam = "id")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                companyMapper.toResponse(companyRepository.findById(id).orElseThrow())));
    }
}
