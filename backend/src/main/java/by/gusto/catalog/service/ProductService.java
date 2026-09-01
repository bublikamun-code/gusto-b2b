package by.gusto.catalog.service;

import by.gusto.catalog.dto.ProductRequest;
import by.gusto.catalog.dto.ProductResponse;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.mapper.ProductMapper;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.service.ProductImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAll(int page, int size, String search, UUID categoryId, UUID brandId, Boolean active) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Product> products = productRepository.findAll(
                by.gusto.catalog.repository.ProductSpecification.filter(categoryId, brandId, active, search),
                pageable);
        Map<UUID, List<String>> imageUrls = imageUrlResolver.resolveUrls(
                products.getContent().stream().map(Product::getId).toList());
        return products.map(p -> enrich(productMapper.toResponse(p), imageUrls.getOrDefault(p.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = findActiveProduct(id);
        ProductResponse response = productMapper.toResponse(product);
        response.setImageUrls(imageUrlResolver.resolveUrls(id));
        return response;
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findBySkuAndDeletedAtIsNull(sku)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Товар не найден"));
        ProductResponse response = productMapper.toResponse(product);
        response.setImageUrls(imageUrlResolver.resolveUrls(product.getId()));
        return response;
    }

    private ProductResponse enrich(ProductResponse response, List<String> imageUrls) {
        response.setImageUrls(imageUrls);
        return response;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        validateSku(request.getSku(), null);
        validateCategory(request.getCategoryId());
        validateBrand(request.getBrandId());
        Product product = productMapper.toEntity(request);
        applyFlags(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findActiveProduct(id);
        validateSku(request.getSku(), id);
        validateCategory(request.getCategoryId());
        validateBrand(request.getBrandId());
        productMapper.updateEntity(product, request);
        applyFlags(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findActiveProduct(id);
        product.setDeletedAt(Instant.now());
        product.setActive(false);
        productRepository.save(product);
    }

    private Product findActiveProduct(UUID id) {
        return productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Товар не найден"));
    }

    private void validateSku(String sku, UUID excludeId) {
        boolean exists = excludeId == null
                ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNotAndDeletedAtIsNull(sku, excludeId);
        if (exists) {
            throw new GustoException(ErrorCode.CONFLICT, "Товар с таким SKU уже существует");
        }
    }

    private void validateCategory(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Категория не найдена");
        }
    }

    private void validateBrand(UUID brandId) {
        if (brandId != null && !brandRepository.existsById(brandId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Бренд не найден");
        }
    }

    private void applyFlags(Product product, ProductRequest request) {
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
    }
}
