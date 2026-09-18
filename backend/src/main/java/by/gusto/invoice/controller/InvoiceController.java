package by.gusto.invoice.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.invoice.dto.InvoiceDtos.CreateRequest;
import by.gusto.invoice.dto.InvoiceDtos.InvoiceResponse;
import by.gusto.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Счета в бэк-офисе (S24). Создание/выпуск/отмена — ADMIN/ACCOUNTANT/MANAGER
 * (матрица 2.1 «Создание счёта/ТН/ТТН»); менеджер видит счета своих клиентов.
 * PDF — S25, платежи — S28.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final AuthContext authContext;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(@Valid @RequestBody CreateRequest request) {
        User actor = authContext.getCurrentUser();
        InvoiceResponse response = invoiceService.createFromOrder(request.getOrderId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InvoiceResponse> result = invoiceService.list(authContext.getCurrentUser(), page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.getById(id, authContext.getCurrentUser())));
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issue(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.issue(id, authContext.getCurrentUser())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.cancel(id, authContext.getCurrentUser())));
    }
}
