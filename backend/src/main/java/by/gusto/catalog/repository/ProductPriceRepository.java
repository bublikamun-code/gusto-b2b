package by.gusto.catalog.repository;

import by.gusto.catalog.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    @Query("""
            SELECT pp FROM ProductPrice pp
            WHERE pp.priceListId = :priceListId
              AND pp.productId IN :productIds
              AND pp.validFrom <= :date
              AND (pp.validTo IS NULL OR pp.validTo >= :date)
            """)
    List<ProductPrice> findActualPrices(
            @Param("priceListId") UUID priceListId,
            @Param("productIds") Collection<UUID> productIds,
            @Param("date") LocalDate date);

    @Query("""
            SELECT pp.price FROM ProductPrice pp
            WHERE pp.priceListId = :priceListId
              AND pp.productId = :productId
              AND pp.validFrom <= :date
              AND (pp.validTo IS NULL OR pp.validTo >= :date)
            ORDER BY pp.validFrom DESC
            LIMIT 1
            """)
    Optional<BigDecimal> findPrice(
            @Param("priceListId") UUID priceListId,
            @Param("productId") UUID productId,
            @Param("date") LocalDate date);
}
