package by.gusto.integration.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Экспорт для 1С (S36): заказы/счета/накладные за период (.xlsx).
 * Права — ADMIN/ACCOUNTANT/MANAGER (матрица 2.1 «Выгрузка файлов для 1С»).
 * S40: не более 10 выгрузок в час на пользователя (rate limit).
 */
@RestController
@RequestMapping("/api/v1/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class AdminExportController {

    private static final java.time.Duration EXPORT_WINDOW = java.time.Duration.ofHours(1);
    private static final int EXPORT_LIMIT = 10;

    private final XlsxExportService exportService;
    private final AuthContext authContext;
    private final by.gusto.common.ratelimit.RequestRateLimiter rateLimiter;

    private void checkExportLimit(by.gusto.auth.entity.User user) {
        rateLimiter.enforcePerUser(user.getId(), "export", EXPORT_LIMIT, EXPORT_WINDOW);
    }

    @GetMapping("/orders")
    public ResponseEntity<InputStreamResource> orders(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        by.gusto.auth.entity.User actor = authContext.getCurrentUser();
        checkExportLimit(actor);
        byte[] bytes = exportService.exportOrders(from, to, actor);
        return xlsx(bytes, "orders-" + from + "_" + to + ".xlsx");
    }

    @GetMapping("/invoices")
    public ResponseEntity<InputStreamResource> invoices(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        by.gusto.auth.entity.User actor = authContext.getCurrentUser();
        checkExportLimit(actor);
        byte[] bytes = exportService.exportInvoices(from, to, actor);
        return xlsx(bytes, "invoices-" + from + "_" + to + ".xlsx");
    }

    @GetMapping("/waybills")
    public ResponseEntity<InputStreamResource> waybills(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        by.gusto.auth.entity.User actor = authContext.getCurrentUser();
        checkExportLimit(actor);
        byte[] bytes = exportService.exportWaybills(from, to, actor);
        return xlsx(bytes, "waybills-" + from + "_" + to + ".xlsx");
    }

    private ResponseEntity<InputStreamResource> xlsx(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(new java.io.ByteArrayInputStream(bytes)));
    }
}
