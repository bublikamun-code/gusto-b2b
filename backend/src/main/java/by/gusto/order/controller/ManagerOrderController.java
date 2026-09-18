package by.gusto.order.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.order.dto.OrderDtos.Response;
import by.gusto.order.dto.OrderDtos.StatusUpdateRequest;
import by.gusto.order.entity.OrderEntity.Status;
import by.gusto.order.service.OrderLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Заказы в работе (S22): список менеджера (свои + пул «не назначено», 2.7),
 * «взять в работу», смена статуса по статус-машине. Права — в сервисе:
 * ADMIN все, MANAGER по своим клиентам; бухгалтеру заказы не доступны (2.1).
 */
@RestController
@RequestMapping("/api/v1/manager/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerOrderController {

    private final OrderLifecycleService lifecycleService;
    private final AuthContext authContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Response>>> list(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Response> result = lifecycleService.managerList(
                authContext.getCurrentUser(), scope, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @PostMapping("/{id}/take")
    public ResponseEntity<ApiResponse<Response>> take(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                lifecycleService.takeInWork(id, authContext.getCurrentUser())));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Response>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                lifecycleService.changeStatus(id, request.getStatus(), actor)));
    }
}
