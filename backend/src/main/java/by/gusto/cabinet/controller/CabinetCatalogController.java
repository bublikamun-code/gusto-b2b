package by.gusto.cabinet.controller;

import by.gusto.auth.service.AuthContext;
import by.gusto.catalog.dto.CabinetProductResponse;
import by.gusto.catalog.dto.ProductFilterRequest;
import by.gusto.cabinet.service.CabinetCatalogService;
import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Каталог кабинета. Данные публичные: тот же перечень и цены отдаёт
 * permitAll-ручка {@code GET /api/v1/catalog/products}, поэтому расширение ролей
 * на чтение ничего не раскрывает сверх витрины.
 *
 * <p>MANAGER/ADMIN добавлены из-за S22: «Заказ от имени клиента»
 * ({@code ManagerOrderCreatePage}) звал {@code /cabinet/catalog} и получал 403 —
 * фича не работала вовсе (аудит 2026-09-30, P1-13). Цена при этом считается по
 * компании самого пользователя, то есть у менеджера показывается розничная; итоговую
 * сумму сервер всё равно считает по ценам выбранного клиента (2.5).
 */
@RestController
@RequestMapping("/api/v1/cabinet/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL','MANAGER','ADMIN')")
public class CabinetCatalogController {

    private final AuthContext authContext;
    private final CabinetCatalogService cabinetCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<CabinetProductResponse>>> getCatalog(ProductFilterRequest filter) {
        UUID companyId = authContext.getCurrentUser().getCompanyId();
        Page<CabinetProductResponse> page = cabinetCatalogService.getProducts(filter, companyId);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(),
                Map.of("page", page.getNumber(), "size", page.getSize(), "total", page.getTotalElements())));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<CabinetProductResponse>> getProduct(@PathVariable String sku) {
        UUID companyId = authContext.getCurrentUser().getCompanyId();
        return ResponseEntity.ok(ApiResponse.success(cabinetCatalogService.getProduct(sku, companyId)));
    }
}
