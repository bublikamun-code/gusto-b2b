package by.gusto.inventory.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.service.StockReportService;
import by.gusto.inventory.service.StockReportService.BalanceRow;
import by.gusto.inventory.service.StockReportService.MovementRow;
import by.gusto.inventory.service.StockReportService.ToOrderRow;
import by.gusto.inventory.service.StockReportService.TurnoverRow;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Отчёты склада (S18.3). Матрица 2.1: склад видят ADMIN/ACCOUNTANT/MANAGER.
 */
@RestController
@RequestMapping("/api/v1/warehouse/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class StockReportController {

    private final StockReportService reportService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<List<BalanceRow>>> balance(
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(reportService.balance(locationId, search)));
    }

    @GetMapping("/turnover")
    public ResponseEntity<ApiResponse<List<TurnoverRow>>> turnover(
            @RequestParam(required = false) UUID locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.turnover(locationId, from, to)));
    }

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<MovementRow>>> movements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.movements(productId, locationId, from, to, limit)));
    }

    @GetMapping("/to-order")
    public ResponseEntity<ApiResponse<List<ToOrderRow>>> toOrder() {
        return ResponseEntity.ok(ApiResponse.success(reportService.toOrder()));
    }
}
