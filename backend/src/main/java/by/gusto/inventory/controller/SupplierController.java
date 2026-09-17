package by.gusto.inventory.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.dto.SupplierDtos.Request;
import by.gusto.inventory.dto.SupplierDtos.Response;
import by.gusto.inventory.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Поставщики (S18.2). Матрица 2.1: склад — ADMIN/ACCOUNTANT/MANAGER.
 */
@RestController
@RequestMapping("/api/v1/warehouse/suppliers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Response>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Response> result = supplierService.search(search, active, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(@Valid @RequestBody Request request) {
        return ResponseEntity.status(201).body(ApiResponse.success(supplierService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Response>> update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.update(id, request)));
    }

    /** Деактивация: история заказов продолжает ссылаться на поставщика. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        supplierService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
