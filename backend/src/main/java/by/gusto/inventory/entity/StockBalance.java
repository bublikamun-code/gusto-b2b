package by.gusto.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Материализованный остаток (3.1): единственная строка, которую лочит резерв
 * (SELECT ... FOR UPDATE). Обновляется в одной транзакции с движением.
 */
@Entity
@Table(name = "stock_balances")
@IdClass(StockBalance.StockBalanceId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockBalance {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Доступно = quantity − reserved. */
    public BigDecimal available() {
        return quantity.subtract(reserved);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockBalanceId implements Serializable {
        private UUID productId;
        private UUID locationId;
    }
}
