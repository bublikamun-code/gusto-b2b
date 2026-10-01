package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.cabinet.dto.CabinetSummaryResponse;
import by.gusto.cabinet.service.CabinetSummaryService;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cabinet")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL')")
public class CabinetController {

    private final AuthContext authContext;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CabinetSummaryService cabinetSummaryService;

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

    /**
     * Сводка для дашборда кабинета. Работает и для юрлица, и для физлица:
     * у розницы расчётной части нет, но заказы показываются (матрица 2.1).
     * Компания берётся только из токена — параметра нет, подставить чужую нельзя.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CabinetSummaryResponse>> getSummary() {
        var user = authContext.getCurrentUser();
        UUID companyId = user.getCompanyId();
        String companyName;
        if (companyId != null) {
            companyName = companyRepository.findById(companyId)
                    .map(Company::getName)
                    .orElse(null);
        } else {
            companyName = "Розничные покупки";
        }
        return ResponseEntity.ok(ApiResponse.success(
                cabinetSummaryService.summary(companyId, user.getId(), companyName)));
    }
}
