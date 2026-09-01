package by.gusto.catalog.repository;

import by.gusto.catalog.entity.CustomerDiscount;
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
public interface CustomerDiscountRepository extends JpaRepository<CustomerDiscount, UUID> {

    @Query("""
            SELECT MAX(cd.discountPercent) FROM CustomerDiscount cd
            WHERE cd.companyId = :companyId
              AND (
                cd.brandId = :brandId
                OR cd.categoryId = :categoryId
              )
              AND cd.validFrom <= :date
              AND (cd.validTo IS NULL OR cd.validTo >= :date)
            """)
    Optional<BigDecimal> findMaxDiscount(
            @Param("companyId") UUID companyId,
            @Param("brandId") UUID brandId,
            @Param("categoryId") UUID categoryId,
            @Param("date") LocalDate date);

    List<CustomerDiscount> findAllByCompanyIdOrderByValidFromDesc(UUID companyId);
}
