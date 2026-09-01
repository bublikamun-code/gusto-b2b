package by.gusto.catalog.repository;

import by.gusto.catalog.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    List<PriceList> findAllByOrderByValidFromDesc();

    @Query("""
            SELECT pl FROM PriceList pl
            WHERE pl.active = true
              AND pl.validFrom <= :date
              AND (pl.validTo IS NULL OR pl.validTo >= :date)
            ORDER BY pl.validFrom DESC
            """)
    List<PriceList> findActiveByDate(@Param("date") LocalDate date);

    @Query("""
            SELECT pl FROM PriceList pl
            WHERE pl.active = true
              AND pl.validFrom <= :date
              AND (pl.validTo IS NULL OR pl.validTo >= :date)
            ORDER BY pl.validFrom DESC
            LIMIT 1
            """)
    Optional<PriceList> findFirstActiveByDate(@Param("date") LocalDate date);
}
