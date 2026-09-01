package by.gusto.catalog.repository;

import by.gusto.catalog.entity.CustomerPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, UUID> {

    @Query("""
            SELECT cp.price FROM CustomerPrice cp
            WHERE cp.companyId = :companyId
              AND cp.productId = :productId
              AND cp.validFrom <= :date
              AND (cp.validTo IS NULL OR cp.validTo >= :date)
            ORDER BY cp.validFrom DESC
            LIMIT 1
            """)
    Optional<BigDecimal> findActualPrice(
            @Param("companyId") UUID companyId,
            @Param("productId") UUID productId,
            @Param("date") LocalDate date);

    List<CustomerPrice> findAllByCompanyIdOrderByValidFromDesc(UUID companyId);
}
