package by.gusto.inventory.repository;

import by.gusto.inventory.entity.StockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalance, StockBalance.StockBalanceId> {

    /** Единственная точка конкурентного доступа в транзакции заказа (1.6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.productId = :productId and b.locationId = :locationId")
    Optional<StockBalance> findForUpdate(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    /** Доступный остаток по набору товаров для статуса наличия в каталоге. */
    @Query("select b.productId as productId, b.quantity - b.reserved as available "
            + "from StockBalance b where b.locationId = :locationId and b.productId in :productIds")
    List<AvailableProjection> findAvailableByLocationAndProductIds(
            @Param("locationId") UUID locationId, @Param("productIds") Collection<UUID> productIds);

    interface AvailableProjection {
        UUID getProductId();
        BigDecimal getAvailable();
    }
}
