package by.gusto.payment.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.payment.dto.PaymentDtos.DebtRow;
import by.gusto.payment.dto.PaymentDtos.PaymentRequest;
import by.gusto.payment.dto.PaymentDtos.PaymentResponse;
import by.gusto.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Платежи и задолженность (S28). Регистрация оплат и отчёт долга —
 * ADMIN/ACCOUNTANT (матрица 2.1); менеджеру долг своих клиентов доступен
 * через дашборд S30.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthContext authContext;

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> register(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(paymentService.register(id, request, actor)));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> list(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.listByInvoice(id)));
    }

    @GetMapping("/debts")
    public ResponseEntity<ApiResponse<List<DebtRow>>> debts() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.debtReport()));
    }
}
