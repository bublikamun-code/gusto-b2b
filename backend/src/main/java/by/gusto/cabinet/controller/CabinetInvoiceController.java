package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.entity.FileEntity;
import by.gusto.invoice.dto.InvoiceDtos.InvoiceResponse;
import by.gusto.invoice.service.InvoicePdfService;
import by.gusto.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Кабинет юрлица: счета своей компании (S24). Физлицу документы не доступны
 * (матрица 2.1). PDF — S25: скачивание только своих счетов.
 */
@RestController
@RequestMapping("/api/v1/cabinet/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','ADMIN')")
public class CabinetInvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
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

    /** PDF своего счёта (S25): PRIVATE-файл, стриминг с проверкой прав (1.6). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> pdf(@PathVariable java.util.UUID id) {
        var user = authContext.getCurrentUser();
        invoiceService.getById(id, user); // проверка принадлежности компании
        FileEntity file = invoicePdfService.ensurePdf(id, user.getId());
        InputStream stream = invoicePdfService.load(file);
        String filename = URLDecoder.decode(file.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(stream));
    }
}
