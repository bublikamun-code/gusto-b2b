package by.gusto.catalog.service;

import by.gusto.catalog.dto.BrandResponse;
import by.gusto.catalog.dto.CatalogProductResponse;
import by.gusto.catalog.dto.CategoryResponse;
import by.gusto.catalog.dto.ProductFilterRequest;
import by.gusto.catalog.entity.Brand;
import by.gusto.catalog.entity.Category;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.mapper.BrandMapper;
import by.gusto.catalog.mapper.CategoryMapper;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.catalog.repository.ProductSpecification;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.service.ProductImageUrlResolver;
import by.gusto.inventory.dto.StockStatus;
import by.gusto.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final RetailPriceService retailPriceService;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductImageUrlResolver imageUrlResolver;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategoryTree() {
        List<Category> categories = categoryRepository.findAllByActiveTrueOrderBySortAsc();
        return buildTree(categories);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands() {
        return brandMapper.toResponseList(brandRepository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public Page<CatalogProductResponse> getProducts(ProductFilterRequest filter) {
        PageRequest pageable = PageRequest.of(
                filter.getPage() == null ? 0 : filter.getPage(),
                filter.getSize() == null ? 20 : filter.getSize(),
                Sort.by("name").ascending());

        Page<Product> page = productRepository.findAll(
                ProductSpecification.filter(filter.getCategoryId(), filter.getBrandId(), true, filter.getSearch()),
                pageable);

        List<Product> products = page.getContent();
        Map<UUID, BigDecimal> prices = retailPriceService.getRetailPrices(
                products.stream().map(Product::getId).collect(Collectors.toSet()));
        Map<UUID, List<String>> imageUrls = imageUrlResolver.resolveUrls(
                products.stream().map(Product::getId).toList());
        Map<UUID, StockStatus> stockStatuses = stockStatuses(products.stream().map(Product::getId).toList());

        return page.map(product -> toCatalogResponse(
                product, prices.get(product.getId()),
                imageUrls.getOrDefault(product.getId(), List.of()),
                stockStatuses.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public CatalogProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySkuAndDeletedAtIsNull(sku)
                .filter(Product::isActive)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Товар не найден"));
        BigDecimal price = retailPriceService.getRetailPrice(product.getId()).orElse(null);
        Map<UUID, StockStatus> statuses = stockStatuses(List.of(product.getId()));
        return toCatalogResponse(product, price, imageUrlResolver.resolveUrls(product.getId()),
                statuses.get(product.getId()));
    }

    /** Статус наличия из доступного остатка склада по умолчанию (S18). */
    private Map<UUID, StockStatus> stockStatuses(List<UUID> productIds) {
        UUID locationId = stockService.defaultLocationId();
        Map<UUID, BigDecimal> available = stockService.availableByProduct(locationId, productIds);
        return productIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> available.getOrDefault(id, BigDecimal.ZERO).signum() > 0
                        ? StockStatus.IN_STOCK
                        : StockStatus.PREORDER));
    }

    private CatalogProductResponse toCatalogResponse(Product product, BigDecimal retailPrice, List<String> imageUrls,
                                                     StockStatus stockStatus) {
        Category category = product.getCategoryId() != null
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;
        Brand brand = product.getBrandId() != null
                ? brandRepository.findById(product.getBrandId()).orElse(null)
                : null;

        return CatalogProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(category != null ? categoryMapper.toResponse(category) : null)
                .brand(brand != null ? brandMapper.toResponse(brand) : null)
                .unit(product.getUnit())
                .description(product.getDescription())
                .retailPrice(retailPrice)
                .stockStatus(stockStatus)
                .hit(product.isHit())
                .newProduct(product.isNewProduct())
                .weightStep(product.getWeightPerUnit())
                .imageUrls(imageUrls)
                .build();
    }

    private List<CategoryResponse> buildTree(List<Category> categories) {
        List<CategoryResponse> all = categoryMapper.toResponseList(categories);
        java.util.Map<UUID, CategoryResponse> map = all.stream()
                .collect(Collectors.toMap(CategoryResponse::getId, r -> r));
        List<CategoryResponse> roots = new java.util.ArrayList<>();
        for (CategoryResponse response : map.values()) {
            if (response.getParentId() == null) {
                roots.add(response);
            } else {
                CategoryResponse parent = map.get(response.getParentId());
                if (parent != null) {
                    parent.getChildren().add(response);
                }
            }
        }
        return roots;
    }
}
