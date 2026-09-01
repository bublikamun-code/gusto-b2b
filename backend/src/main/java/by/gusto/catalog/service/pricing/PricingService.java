package by.gusto.catalog.service.pricing;

import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.CustomerDiscountRepository;
import by.gusto.catalog.repository.CustomerPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.catalog.service.RetailPriceService;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final CustomerPriceRepository customerPriceRepository;
    private final CustomerDiscountRepository customerDiscountRepository;
    private final RetailPriceService retailPriceService;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCustomerPrice(UUID companyId, UUID productId) {
        return getCustomerPrice(companyId, productId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCustomerPrice(UUID companyId, UUID productId, LocalDate date) {
        if (companyId == null || productId == null) {
            return Optional.empty();
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Товар не найден"));
        return getCustomerPrice(companyId, product, date);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCustomerPrice(UUID companyId, Product product) {
        return getCustomerPrice(companyId, product, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getCustomerPrice(UUID companyId, Product product, LocalDate date) {
        if (companyId == null || product == null) {
            return Optional.empty();
        }

        // 1. Персональная цена
        Optional<BigDecimal> personalPrice = customerPriceRepository.findActualPrice(
                companyId, product.getId(), date);
        if (personalPrice.isPresent()) {
            return personalPrice;
        }

        // 2. Базовая цена из активного прайс-листа
        Optional<BigDecimal> basePrice = retailPriceService.getRetailPrice(product.getId(), date);
        if (basePrice.isEmpty()) {
            return Optional.empty();
        }

        // 3. Скидка клиента по бренду/категории — берём максимальную
        Optional<BigDecimal> maxDiscount = customerDiscountRepository.findMaxDiscount(
                companyId,
                product.getBrandId(),
                product.getCategoryId(),
                date);

        if (maxDiscount.isEmpty() || maxDiscount.get().compareTo(BigDecimal.ZERO) <= 0) {
            return basePrice;
        }

        BigDecimal discount = maxDiscount.get();
        BigDecimal multiplier = BigDecimal.valueOf(100).subtract(discount)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return Optional.of(basePrice.get().multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP));
    }
}
