package by.gusto.catalog.controller;

import by.gusto.catalog.dto.BrandResponse;
import by.gusto.catalog.dto.CatalogProductResponse;
import by.gusto.catalog.dto.CategoryResponse;
import by.gusto.catalog.dto.ProductFilterRequest;
import by.gusto.catalog.service.CatalogService;
import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getActiveCategoryTree()));
    }

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getAllBrands()));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<CatalogProductResponse>>> getProducts(ProductFilterRequest filter) {
        Page<CatalogProductResponse> page = catalogService.getProducts(filter);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(),
                Map.of("page", page.getNumber(), "size", page.getSize(), "total", page.getTotalElements())));
    }

    @GetMapping("/products/{sku}")
    public ResponseEntity<ApiResponse<CatalogProductResponse>> getProduct(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getProductBySku(sku)));
    }
}
