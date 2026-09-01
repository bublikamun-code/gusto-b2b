package by.gusto.catalog.service.pricing;

import by.gusto.catalog.dto.CustomerDiscountRequest;
import by.gusto.catalog.dto.CustomerPriceRequest;
import by.gusto.catalog.dto.PriceListRequest;
import by.gusto.catalog.dto.ProductPriceRequest;
import by.gusto.catalog.entity.CustomerDiscount;
import by.gusto.catalog.entity.CustomerPrice;
import by.gusto.catalog.entity.PriceList;
import by.gusto.catalog.entity.ProductPrice;
import by.gusto.catalog.mapper.CustomerDiscountMapper;
import by.gusto.catalog.mapper.CustomerPriceMapper;
import by.gusto.catalog.mapper.PriceListMapper;
import by.gusto.catalog.mapper.ProductPriceMapper;
import by.gusto.catalog.repository.BrandRepository;
import by.gusto.catalog.repository.CategoryRepository;
import by.gusto.catalog.repository.CustomerDiscountRepository;
import by.gusto.catalog.repository.CustomerPriceRepository;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingAdminService {

    private final PriceListRepository priceListRepository;
    private final ProductPriceRepository productPriceRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final CustomerDiscountRepository customerDiscountRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    private final PriceListMapper priceListMapper;
    private final ProductPriceMapper productPriceMapper;
    private final CustomerPriceMapper customerPriceMapper;
    private final CustomerDiscountMapper customerDiscountMapper;

    // Price lists

    @Transactional(readOnly = true)
    public List<PriceList> getAllPriceLists() {
        return priceListRepository.findAllByOrderByValidFromDesc();
    }

    @Transactional(readOnly = true)
    public PriceList getPriceList(UUID id) {
        return priceListRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Прайс-лист не найден"));
    }

    @Transactional
    public PriceList createPriceList(PriceListRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        PriceList entity = priceListMapper.toEntity(request);
        return priceListRepository.save(entity);
    }

    @Transactional
    public PriceList updatePriceList(UUID id, PriceListRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        PriceList entity = getPriceList(id);
        priceListMapper.updateEntity(entity, request);
        return priceListRepository.save(entity);
    }

    @Transactional
    public void deletePriceList(UUID id) {
        priceListRepository.deleteById(id);
    }

    // Product prices

    @Transactional(readOnly = true)
    public List<ProductPrice> getProductPricesByPriceList(UUID priceListId) {
        return productPriceRepository.findAllByPriceListIdOrderByValidFromDesc(priceListId);
    }

    @Transactional(readOnly = true)
    public ProductPrice getProductPrice(UUID id) {
        return productPriceRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Цена товара не найдена"));
    }

    @Transactional
    public ProductPrice createProductPrice(ProductPriceRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validatePriceList(request.getPriceListId());
        validateProduct(request.getProductId());
        ProductPrice entity = productPriceMapper.toEntity(request);
        return productPriceRepository.save(entity);
    }

    @Transactional
    public ProductPrice updateProductPrice(UUID id, ProductPriceRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validatePriceList(request.getPriceListId());
        validateProduct(request.getProductId());
        ProductPrice entity = getProductPrice(id);
        productPriceMapper.updateEntity(entity, request);
        return productPriceRepository.save(entity);
    }

    @Transactional
    public void deleteProductPrice(UUID id) {
        productPriceRepository.deleteById(id);
    }

    // Customer prices

    @Transactional(readOnly = true)
    public List<CustomerPrice> getCustomerPricesByCompany(UUID companyId) {
        return customerPriceRepository.findAllByCompanyIdOrderByValidFromDesc(companyId);
    }

    @Transactional(readOnly = true)
    public CustomerPrice getCustomerPriceEntity(UUID id) {
        return customerPriceRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Персональная цена не найдена"));
    }

    @Transactional
    public CustomerPrice createCustomerPrice(CustomerPriceRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validateCompany(request.getCompanyId());
        validateProduct(request.getProductId());
        CustomerPrice entity = customerPriceMapper.toEntity(request);
        return customerPriceRepository.save(entity);
    }

    @Transactional
    public CustomerPrice updateCustomerPrice(UUID id, CustomerPriceRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validateCompany(request.getCompanyId());
        validateProduct(request.getProductId());
        CustomerPrice entity = getCustomerPriceEntity(id);
        customerPriceMapper.updateEntity(entity, request);
        return customerPriceRepository.save(entity);
    }

    @Transactional
    public void deleteCustomerPrice(UUID id) {
        customerPriceRepository.deleteById(id);
    }

    // Customer discounts

    @Transactional(readOnly = true)
    public List<CustomerDiscount> getCustomerDiscountsByCompany(UUID companyId) {
        return customerDiscountRepository.findAllByCompanyIdOrderByValidFromDesc(companyId);
    }

    @Transactional(readOnly = true)
    public CustomerDiscount getCustomerDiscountEntity(UUID id) {
        return customerDiscountRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Скидка не найдена"));
    }

    @Transactional
    public CustomerDiscount createCustomerDiscount(CustomerDiscountRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validateCompany(request.getCompanyId());
        validateDiscountTarget(request.getBrandId(), request.getCategoryId());
        validateDiscountPercent(request.getDiscountPercent());
        CustomerDiscount entity = customerDiscountMapper.toEntity(request);
        return customerDiscountRepository.save(entity);
    }

    @Transactional
    public CustomerDiscount updateCustomerDiscount(UUID id, CustomerDiscountRequest request) {
        validatePeriod(request.getValidFrom(), request.getValidTo());
        validateCompany(request.getCompanyId());
        validateDiscountTarget(request.getBrandId(), request.getCategoryId());
        validateDiscountPercent(request.getDiscountPercent());
        CustomerDiscount entity = getCustomerDiscountEntity(id);
        customerDiscountMapper.updateEntity(entity, request);
        return customerDiscountRepository.save(entity);
    }

    @Transactional
    public void deleteCustomerDiscount(UUID id) {
        customerDiscountRepository.deleteById(id);
    }

    // Validation helpers

    private void validatePeriod(LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "validTo не может быть раньше validFrom");
        }
    }

    private void validatePriceList(UUID priceListId) {
        if (!priceListRepository.existsById(priceListId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Прайс-лист не найден");
        }
    }

    private void validateProduct(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Товар не найден");
        }
    }

    private void validateCompany(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Компания не найдена");
        }
    }

    private void validateDiscountTarget(UUID brandId, UUID categoryId) {
        if (brandId == null && categoryId == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Укажите brandId или categoryId для скидки");
        }
        if (brandId != null && !brandRepository.existsById(brandId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Бренд не найден");
        }
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Категория не найдена");
        }
    }

    private void validateDiscountPercent(BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Процент скидки должен быть от 0 до 100");
        }
    }
}
