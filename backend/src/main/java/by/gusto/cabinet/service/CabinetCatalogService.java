package by.gusto.cabinet.service;

import by.gusto.catalog.dto.CabinetProductResponse;
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
import by.gusto.catalog.service.RetailPriceService;
import by.gusto.catalog.service.pricing.PricingService;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.service.ProductImageUrlResolver;
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
public class CabinetCatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final RetailPriceService retailPriceService;
    private final PricingService pricingService;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public Page<CabinetProductResponse> getProducts(ProductFilterRequest filter, UUID companyId) {
        PageRequest pageable = PageRequest.of(
                filter.getPage() == null ? 0 : filter.getPage(),
                filter.getSize() == null ? 20 : filter.getSize(),
                Sort.by("name").ascending());

        Page<Product> page = productRepository.findAll(
                ProductSpecification.filter(filter.getCategoryId(), filter.getBrandId(), true, filter.getSearch()),
                pageable);

        Map<UUID, BigDecimal> retailPrices = retailPriceService.getRetailPrices(
                page.getContent().stream().map(Product::getId).collect(Collectors.toSet()));
        Map<UUID, List<String>> imageUrls = imageUrlResolver.resolveUrls(
                page.getContent().stream().map(Product::getId).toList());

        return page.map(product -> toCabinetResponse(
                product, companyId, retailPrices.get(product.getId()),
                imageUrls.getOrDefault(product.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public CabinetProductResponse getProduct(String sku, UUID companyId) {
        Product product = productRepository.findBySkuAndDeletedAtIsNull(sku)
                .filter(Product::isActive)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Товар не найден"));
        BigDecimal retailPrice = retailPriceService.getRetailPrice(product.getId()).orElse(null);
        return toCabinetResponse(product, companyId, retailPrice, imageUrlResolver.resolveUrls(product.getId()));
    }

    private CabinetProductResponse toCabinetResponse(Product product, UUID companyId, BigDecimal retailPrice, List<String> imageUrls) {
        Category category = product.getCategoryId() != null
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;
        Brand brand = product.getBrandId() != null
                ? brandRepository.findById(product.getBrandId()).orElse(null)
                : null;

        BigDecimal customerPrice = pricingService.getCustomerPrice(companyId, product).orElse(retailPrice);

        return CabinetProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(category != null ? categoryMapper.toResponse(category) : null)
                .brand(brand != null ? brandMapper.toResponse(brand) : null)
                .unit(product.getUnit())
                .description(product.getDescription())
                .retailPrice(retailPrice)
                .customerPrice(customerPrice)
                .imageUrls(imageUrls)
                .build();
    }
}
