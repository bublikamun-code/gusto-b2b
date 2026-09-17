package by.gusto.inventory.controller;

import by.gusto.common.api.ApiResponse;
import by.gusto.inventory.entity.StockLocation;
import by.gusto.inventory.repository.StockLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Справочник складов (S18.4 UI). Матрица 2.1: склад — ADMIN/ACCOUNTANT/MANAGER.
 */
@RestController
@RequestMapping("/api/v1/warehouse/locations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class StockLocationController {

    private final StockLocationRepository locationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockLocation>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                locationRepository.findAllByActiveTrueOrderByNameAsc()));
    }
}
