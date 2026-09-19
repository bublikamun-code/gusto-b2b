package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
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

/**
 * Кабинет юрлица: выгрузка прайса со своими ценами в .xlsx (S36, матрица 2.1).
 */
@RestController
@RequestMapping("/api/v1/cabinet/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','ADMIN')")
public class CabinetPricingExportController {

    private final XlsxExportService exportService;
    private final AuthContext authContext;

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
