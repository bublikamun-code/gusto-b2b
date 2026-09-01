package by.gusto.catalog.controller.admin;

import by.gusto.catalog.dto.CustomerDiscountRequest;
import by.gusto.catalog.dto.CustomerDiscountResponse;
import by.gusto.catalog.dto.CustomerPriceRequest;
import by.gusto.catalog.dto.CustomerPriceResponse;
import by.gusto.catalog.dto.PriceListRequest;
import by.gusto.catalog.dto.PriceListResponse;
import by.gusto.catalog.dto.ProductPriceRequest;
import by.gusto.catalog.dto.ProductPriceResponse;
import by.gusto.catalog.mapper.CustomerDiscountMapper;
import by.gusto.catalog.mapper.CustomerPriceMapper;
import by.gusto.catalog.mapper.PriceListMapper;
import by.gusto.catalog.mapper.ProductPriceMapper;
import by.gusto.catalog.service.pricing.PricingAdminService;
import by.gusto.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingAdminService pricingAdminService;
    private final PriceListMapper priceListMapper;
    private final ProductPriceMapper productPriceMapper;
    private final CustomerPriceMapper customerPriceMapper;
    private final CustomerDiscountMapper customerDiscountMapper;

    // Price lists

    @GetMapping("/price-lists")
    public ResponseEntity<ApiResponse<List<PriceListResponse>>> getPriceLists() {
        return ResponseEntity.ok(ApiResponse.success(
                priceListMapper.toResponseList(pricingAdminService.getAllPriceLists())));
    }

    @PostMapping("/price-lists")
    public ResponseEntity<ApiResponse<PriceListResponse>> createPriceList(
            @Valid @RequestBody PriceListRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                priceListMapper.toResponse(pricingAdminService.createPriceList(request))));
    }

    @GetMapping("/price-lists/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> getPriceList(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                priceListMapper.toResponse(pricingAdminService.getPriceList(id))));
    }

    @PutMapping("/price-lists/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> updatePriceList(
            @PathVariable UUID id,
            @Valid @RequestBody PriceListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                priceListMapper.toResponse(pricingAdminService.updatePriceList(id, request))));
    }

    @DeleteMapping("/price-lists/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePriceList(@PathVariable UUID id) {
        pricingAdminService.deletePriceList(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Product prices

    @GetMapping("/price-lists/{priceListId}/prices")
    public ResponseEntity<ApiResponse<List<ProductPriceResponse>>> getProductPrices(
            @PathVariable UUID priceListId) {
        return ResponseEntity.ok(ApiResponse.success(
                productPriceMapper.toResponseList(pricingAdminService.getProductPricesByPriceList(priceListId))));
    }

    @PostMapping("/product-prices")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> createProductPrice(
            @Valid @RequestBody ProductPriceRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                productPriceMapper.toResponse(pricingAdminService.createProductPrice(request))));
    }

    @GetMapping("/product-prices/{id}")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> getProductPrice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                productPriceMapper.toResponse(pricingAdminService.getProductPrice(id))));
    }

    @PutMapping("/product-prices/{id}")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> updateProductPrice(
            @PathVariable UUID id,
            @Valid @RequestBody ProductPriceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productPriceMapper.toResponse(pricingAdminService.updateProductPrice(id, request))));
    }

    @DeleteMapping("/product-prices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProductPrice(@PathVariable UUID id) {
        pricingAdminService.deleteProductPrice(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Customer prices

    @GetMapping("/companies/{companyId}/customer-prices")
    public ResponseEntity<ApiResponse<List<CustomerPriceResponse>>> getCustomerPrices(
            @PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.success(
                customerPriceMapper.toResponseList(pricingAdminService.getCustomerPricesByCompany(companyId))));
    }

    @PostMapping("/customer-prices")
    public ResponseEntity<ApiResponse<CustomerPriceResponse>> createCustomerPrice(
            @Valid @RequestBody CustomerPriceRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                customerPriceMapper.toResponse(pricingAdminService.createCustomerPrice(request))));
    }

    @GetMapping("/customer-prices/{id}")
    public ResponseEntity<ApiResponse<CustomerPriceResponse>> getCustomerPrice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                customerPriceMapper.toResponse(pricingAdminService.getCustomerPriceEntity(id))));
    }

    @PutMapping("/customer-prices/{id}")
    public ResponseEntity<ApiResponse<CustomerPriceResponse>> updateCustomerPrice(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerPriceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerPriceMapper.toResponse(pricingAdminService.updateCustomerPrice(id, request))));
    }

    @DeleteMapping("/customer-prices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomerPrice(@PathVariable UUID id) {
        pricingAdminService.deleteCustomerPrice(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Customer discounts

    @GetMapping("/companies/{companyId}/customer-discounts")
    public ResponseEntity<ApiResponse<List<CustomerDiscountResponse>>> getCustomerDiscounts(
            @PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.success(
                customerDiscountMapper.toResponseList(pricingAdminService.getCustomerDiscountsByCompany(companyId))));
    }

    @PostMapping("/customer-discounts")
    public ResponseEntity<ApiResponse<CustomerDiscountResponse>> createCustomerDiscount(
            @Valid @RequestBody CustomerDiscountRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                customerDiscountMapper.toResponse(pricingAdminService.createCustomerDiscount(request))));
    }

    @GetMapping("/customer-discounts/{id}")
    public ResponseEntity<ApiResponse<CustomerDiscountResponse>> getCustomerDiscount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                customerDiscountMapper.toResponse(pricingAdminService.getCustomerDiscountEntity(id))));
    }

    @PutMapping("/customer-discounts/{id}")
    public ResponseEntity<ApiResponse<CustomerDiscountResponse>> updateCustomerDiscount(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerDiscountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerDiscountMapper.toResponse(pricingAdminService.updateCustomerDiscount(id, request))));
    }

    @DeleteMapping("/customer-discounts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomerDiscount(@PathVariable UUID id) {
        pricingAdminService.deleteCustomerDiscount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
