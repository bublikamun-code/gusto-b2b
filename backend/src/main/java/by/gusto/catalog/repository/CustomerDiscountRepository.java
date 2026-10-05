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

    /**
     * Максимальная применимая скидка клиента.
     *
     * <p>Ограничения строки соединяются через И, а не через ИЛИ. Раньше стояло
     * {@code (brandId = :brandId OR categoryId = :categoryId)}, из-за чего скидка,
     * заданная на пару «бренд X в категории Y», раздавалась на ВЕСЬ бренд X и на
     * ВСЮ категорию Y — прямая потеря выручки (аудит 2026-09-30, группа «Цифры»).
     *
     * <p>NULL означает «без ограничения»: строка только с брендом действует на весь
     * бренд, только с категорией — на всю категорию, без обоих — на весь каталог,
     * с обоими — ровно на их пересечение.
     */
    @Query("""
            SELECT MAX(cd.discountPercent) FROM CustomerDiscount cd
            WHERE cd.companyId = :companyId
              AND (cd.brandId IS NULL OR cd.brandId = :brandId)
              AND (cd.categoryId IS NULL OR cd.categoryId = :categoryId)
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
