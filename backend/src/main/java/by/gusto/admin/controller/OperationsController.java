package by.gusto.admin.controller;

import by.gusto.admin.service.OperationsDashboardService;
import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Дашборд процессов (S38): сводка операционного центра для стартовой
 * страницы админки. Права — ADMIN/ACCOUNTANT (матрица 2.1 «Дашборды»).
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class OperationsController {

    private final OperationsDashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<OperationsDashboardService.Dashboard>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.build()));
    }
}
