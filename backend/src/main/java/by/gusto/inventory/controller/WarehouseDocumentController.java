package by.gusto.inventory.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.dto.WarehouseDocumentDtos.CreateRequest;
import by.gusto.inventory.dto.WarehouseDocumentDtos.Response;
import by.gusto.inventory.entity.WarehouseDocument;
import by.gusto.inventory.service.WarehouseDocumentService;
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

import java.util.Map;
import java.util.UUID;

/**
 * Складские документы (S18.1). Матрица 2.1: приход/расход/списание —
 * ADMIN, ACCOUNTANT, MANAGER.
 */
@RestController
@RequestMapping("/api/v1/warehouse/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class WarehouseDocumentController {

    private final WarehouseDocumentService documentService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<Response>>> search(
            @RequestParam(required = false) WarehouseDocument.Type type,
            @RequestParam(required = false) WarehouseDocument.Status status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Response> result = documentService.search(type, status, locationId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(@Valid @RequestBody CreateRequest request) {
        Response response = documentService.create(request, authContext.getCurrentUser().getId());
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Response>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.confirm(id, authContext.getCurrentUser().getId())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Response>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentService.cancel(id)));
    }
}
