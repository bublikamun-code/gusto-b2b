package by.gusto.catalog.service;

import by.gusto.catalog.entity.PriceList;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RetailPriceService {

    private final PriceListRepository priceListRepository;
    private final ProductPriceRepository productPriceRepository;

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getRetailPrice(UUID productId) {
        return getRetailPrice(productId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getRetailPrice(UUID productId, LocalDate date) {
        return priceListRepository.findFirstActiveByDate(date)
                .flatMap(priceList -> productPriceRepository.findPrice(priceList.getId(), productId, date));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getRetailPrices(java.util.Collection<UUID> productIds) {
        return getRetailPrices(productIds, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getRetailPrices(java.util.Collection<UUID> productIds, LocalDate date) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Optional<PriceList> activeList = priceListRepository.findFirstActiveByDate(date);
        if (activeList.isEmpty()) {
            return Collections.emptyMap();
        }
        return productPriceRepository.findActualPrices(activeList.get().getId(), productIds, date).stream()
                .collect(Collectors.toMap(
                        pp -> pp.getProductId(),
                        pp -> pp.getPrice(),
                        (first, second) -> first));
    }
}
