package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.invoice.dto.InvoiceDtos.InvoiceResponse;
import by.gusto.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Кабинет юрлица: счета своей компании (S24). Физлицу документы не доступны
 * (матрица 2.1); скачивание PDF — с S25/S27.
 */
@RestController
@RequestMapping("/api/v1/cabinet/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','ADMIN')")
public class CabinetInvoiceController {

    private final InvoiceService invoiceService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var user = authContext.getCurrentUser();
        if (user.getCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
        }
        Page<InvoiceResponse> result = invoiceService.listForCompany(user.getCompanyId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }
}
