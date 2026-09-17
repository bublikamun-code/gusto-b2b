package by.gusto.inventory.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.dto.PurchaseOrderDtos.CreateRequest;
import by.gusto.inventory.dto.PurchaseOrderDtos.Response;
import by.gusto.inventory.entity.PurchaseOrder;
import by.gusto.inventory.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import java.util.Map;
import java.util.UUID;

/**
 * Заказы поставщикам (S18.2). Матрица 2.1: ADMIN/ACCOUNTANT/MANAGER.
 */
@RestController
@RequestMapping("/api/v1/warehouse/purchase-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Response>>> search(
            @RequestParam(required = false) PurchaseOrder.Status status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Response> result = orderService.search(status, supplierId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(@Valid @RequestBody CreateRequest request) {
        Response response = orderService.create(request, authContext.getCurrentUser().getId());
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<Response>> send(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.send(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Response>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancel(id)));
    }
}
