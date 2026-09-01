package by.gusto.catalog.controller.admin;

import by.gusto.catalog.dto.BrandRequest;
import by.gusto.catalog.dto.BrandResponse;
import by.gusto.catalog.dto.CategoryRequest;
import by.gusto.catalog.dto.CategoryResponse;
import by.gusto.catalog.dto.ProductFilterRequest;
import by.gusto.catalog.dto.ProductRequest;
import by.gusto.catalog.dto.ProductResponse;
import by.gusto.catalog.service.BrandService;
import by.gusto.catalog.service.CategoryService;
import by.gusto.catalog.service.ProductService;
import by.gusto.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
public class AdminCatalogController {

    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductService productService;

    // Categories

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAll()));
    }

    @GetMapping("/categories/tree")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categoryTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getActiveTree()));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(categoryService.create(request)));
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Brands

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> listBrands() {
        return ResponseEntity.ok(ApiResponse.success(brandService.getAll()));
    }

    @PostMapping("/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(brandService.create(request)));
    }

    @GetMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrand(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(brandService.getById(id)));
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.success(brandService.update(id, request)));
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable UUID id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Products

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listProducts(ProductFilterRequest filter) {
        Page<ProductResponse> page = productService.getAll(
                filter.getPage(), filter.getSize(), filter.getSearch(),
                filter.getCategoryId(), filter.getBrandId(), null);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(),
                Map.of("page", page.getNumber(), "size", page.getSize(), "total", page.getTotalElements())));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(productService.create(request)));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(id, request)));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
