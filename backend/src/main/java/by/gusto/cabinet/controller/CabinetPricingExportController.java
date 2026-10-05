package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.cabinet.dto.ClientDiscountResponse;
import by.gusto.cabinet.service.CabinetCatalogService;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.integration.service.XlsxExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Кабинет юрлица: прайс со своими ценами (S36, матрица 2.1) — список правил скидок
 * и выгрузка .xlsx для 1С.
 */
@RestController
@RequestMapping("/api/v1/cabinet/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','ADMIN')")
public class CabinetPricingExportController {

    private final XlsxExportService exportService;
    private final AuthContext authContext;
    private final CabinetCatalogService cabinetCatalogService;

    /**
     * Скидки, действующие на компанию клиента. Сами цены клиент берёт из
     * {@code GET /cabinet/catalog} — там уже считается customerPrice по 2.5.
     */
    @GetMapping("/discounts")
    public ResponseEntity<ApiResponse<List<ClientDiscountResponse>>> discounts() {
        var user = authContext.getCurrentUser();
        if (user.getCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
        }
        return ResponseEntity.ok(ApiResponse.success(
                cabinetCatalogService.getClientDiscounts(user.getCompanyId())));
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> export() {
        var user = authContext.getCurrentUser();
        if (user.getCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
        }
        byte[] bytes = exportService.exportClientPricing(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("pricing.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }
}
