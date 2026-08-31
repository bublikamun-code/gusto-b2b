package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cabinet")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL')")
public class CabinetController {

    private final AuthContext authContext;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    @GetMapping("/company")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMyCompany() {
        var companyId = authContext.getCurrentUser().getCompanyId();
        if (companyId == null) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Компания не привязана");
        }
        return ResponseEntity.ok(ApiResponse.success(
                companyMapper.toResponse(companyRepository.findById(companyId)
                        .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND)))));
    }
}
