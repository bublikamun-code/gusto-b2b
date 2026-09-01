package by.gusto.catalog.service.pricing;

import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.CustomerDiscountRepository;
import by.gusto.catalog.repository.CustomerPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.catalog.service.RetailPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private CustomerPriceRepository customerPriceRepository;

    @Mock
    private CustomerDiscountRepository customerDiscountRepository;

    @Mock
    private RetailPriceService retailPriceService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PricingService pricingService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID brandId = UUID.randomUUID();
    private final LocalDate date = LocalDate.now();

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(productId)
                .sku("SKU-001")
                .name("Колбаса")
                .categoryId(categoryId)
                .brandId(brandId)
                .unit("кг")
                .active(true)
                .build();
    }

    @Test
    void personalPriceHasHighestPriority() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(customerPriceRepository.findActualPrice(companyId, productId, date))
                .thenReturn(Optional.of(BigDecimal.valueOf(15.00)));

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, productId, date);

        assertThat(price).isPresent().hasValue(BigDecimal.valueOf(15.00));
    }

    @Test
    void basePriceReturnedWhenNoDiscounts() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(customerPriceRepository.findActualPrice(companyId, productId, date)).thenReturn(Optional.empty());
        when(retailPriceService.getRetailPrice(productId, date)).thenReturn(Optional.of(BigDecimal.valueOf(25.00)));
        when(customerDiscountRepository.findMaxDiscount(companyId, brandId, categoryId, date))
                .thenReturn(Optional.empty());

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, productId, date);

        assertThat(price).isPresent().hasValue(BigDecimal.valueOf(25.00));
    }

    @Test
    void maxDiscountAppliedToBasePrice() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(customerPriceRepository.findActualPrice(companyId, productId, date)).thenReturn(Optional.empty());
        when(retailPriceService.getRetailPrice(productId, date)).thenReturn(Optional.of(BigDecimal.valueOf(100.00)));
        when(customerDiscountRepository.findMaxDiscount(companyId, brandId, categoryId, date))
                .thenReturn(Optional.of(BigDecimal.valueOf(20.00)));

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, productId, date);

        assertThat(price).isPresent().hasValue(new BigDecimal("80.00"));
    }

    @Test
    void zeroDiscountReturnsBasePrice() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(customerPriceRepository.findActualPrice(companyId, productId, date)).thenReturn(Optional.empty());
        when(retailPriceService.getRetailPrice(productId, date)).thenReturn(Optional.of(BigDecimal.valueOf(50.00)));
        when(customerDiscountRepository.findMaxDiscount(companyId, brandId, categoryId, date))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, productId, date);

        assertThat(price).isPresent().hasValue(BigDecimal.valueOf(50.00));
    }

    @Test
    void emptyResultWhenNoBasePrice() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(customerPriceRepository.findActualPrice(companyId, productId, date)).thenReturn(Optional.empty());
        when(retailPriceService.getRetailPrice(productId, date)).thenReturn(Optional.empty());

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, productId, date);

        assertThat(price).isEmpty();
    }

    @Test
    void productPassedDirectlyAvoidsRepositoryLookup() {
        when(customerPriceRepository.findActualPrice(companyId, productId, date))
                .thenReturn(Optional.of(BigDecimal.valueOf(42.00)));

        Optional<BigDecimal> price = pricingService.getCustomerPrice(companyId, product, date);

        assertThat(price).isPresent().hasValue(BigDecimal.valueOf(42.00));
    }
}
