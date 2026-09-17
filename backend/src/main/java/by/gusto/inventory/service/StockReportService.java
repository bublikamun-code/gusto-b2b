package by.gusto.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Отчёты склада (S18.3) поверх вьюх V9/V12 и журнала движений.
 */
@Service
@RequiredArgsConstructor
public class StockReportService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<BalanceRow> balance(UUID locationId, String search) {
        StringBuilder sql = new StringBuilder(
                "select product_id, sku, product_name, location_id, location_name, quantity, reserved "
                        + "from v_stock_balance where true");
        List<Object> params = new ArrayList<>();
        if (locationId != null) {
            sql.append(" and location_id = ?");
            params.add(locationId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" and (product_name ilike ? or sku ilike ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        sql.append(" order by product_name asc");
        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, i) -> {
            BalanceRow row = new BalanceRow();
            row.setProductId(rs.getObject("product_id", UUID.class));
            row.setSku(rs.getString("sku"));
            row.setProductName(rs.getString("product_name"));
            row.setLocationId(rs.getObject("location_id", UUID.class));
            row.setLocationName(rs.getString("location_name"));
            row.setQuantity(rs.getBigDecimal("quantity"));
            row.setReserved(rs.getBigDecimal("reserved"));
            row.setAvailable(row.getQuantity().subtract(row.getReserved()));
            return row;
        });
    }

    @Transactional(readOnly = true)
    public List<TurnoverRow> turnover(UUID locationId, Instant from, Instant to) {
        String sql = """
                select m.product_id,
                       p.sku,
                       p.name as product_name,
                       coalesce(sum(case when m.type = 'INCOMING' then m.quantity end), 0)  as incoming,
                       coalesce(sum(case when m.type = 'OUTGOING' then -m.quantity end), 0) as outgoing
                from stock_movements m
                join products p on p.id = m.product_id
                where m.type in ('INCOMING','OUTGOING')
                  and m.created_at >= ? and m.created_at < ?
                  and (?::uuid is null or m.location_id = ?::uuid)
                group by m.product_id, p.sku, p.name
                order by p.name asc
                """;
        return jdbcTemplate.query(sql, (rs, i) -> {
            TurnoverRow row = new TurnoverRow();
            row.setProductId(rs.getObject("product_id", UUID.class));
            row.setSku(rs.getString("sku"));
            row.setProductName(rs.getString("product_name"));
            row.setIncoming(rs.getBigDecimal("incoming"));
            row.setOutgoing(rs.getBigDecimal("outgoing"));
            return row;
        }, Timestamp.from(from), Timestamp.from(to), locationId == null ? null : locationId.toString(),
                locationId == null ? null : locationId.toString());
    }

    @Transactional(readOnly = true)
    public List<MovementRow> movements(UUID productId, UUID locationId, Instant from, Instant to, int limit) {
        String sql = """
                select m.product_id,
                       p.sku,
                       p.name as product_name,
                       m.location_id,
                       l.name as location_name,
                       m.type,
                       m.quantity,
                       m.reference_type,
                       m.note,
                       m.created_at
                from stock_movements m
                join products p on p.id = m.product_id
                join stock_locations l on l.id = m.location_id
                where m.created_at >= ? and m.created_at < ?
                  and (?::uuid is null or m.product_id = ?::uuid)
                  and (?::uuid is null or m.location_id = ?::uuid)
                order by m.created_at desc
                limit ?
                """;
        return jdbcTemplate.query(sql, (rs, i) -> {
            MovementRow row = new MovementRow();
            row.setProductId(rs.getObject("product_id", UUID.class));
            row.setSku(rs.getString("sku"));
            row.setProductName(rs.getString("product_name"));
            row.setLocationId(rs.getObject("location_id", UUID.class));
            row.setLocationName(rs.getString("location_name"));
            row.setType(rs.getString("type"));
            row.setQuantity(rs.getBigDecimal("quantity"));
            row.setReferenceType(rs.getString("reference_type"));
            row.setNote(rs.getString("note"));
            row.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            return row;
        }, Timestamp.from(from), Timestamp.from(to),
                productId == null ? null : productId.toString(),
                productId == null ? null : productId.toString(),
                locationId == null ? null : locationId.toString(),
                locationId == null ? null : locationId.toString(),
                Math.min(limit, 500));
    }

    @Transactional(readOnly = true)
    public List<ToOrderRow> toOrder() {
        String sql = "select product_id, sku, product_name, min_stock, available from v_stock_to_order order by product_name";
        return jdbcTemplate.query(sql, (rs, i) -> {
            ToOrderRow row = new ToOrderRow();
            row.setProductId(rs.getObject("product_id", UUID.class));
            row.setSku(rs.getString("sku"));
            row.setProductName(rs.getString("product_name"));
            row.setMinStock(rs.getBigDecimal("min_stock"));
            row.setAvailable(rs.getBigDecimal("available"));
            return row;
        });
    }

    public static class BalanceRow {
        private UUID productId;
        private String sku;
        private String productName;
        private UUID locationId;
        private String locationName;
        private BigDecimal quantity;
        private BigDecimal reserved;
        private BigDecimal available;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getReserved() { return reserved; }
        public void setReserved(BigDecimal reserved) { this.reserved = reserved; }
        public BigDecimal getAvailable() { return available; }
        public void setAvailable(BigDecimal available) { this.available = available; }
    }

    public static class TurnoverRow {
        private UUID productId;
        private String sku;
        private String productName;
        private BigDecimal incoming;
        private BigDecimal outgoing;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getIncoming() { return incoming; }
        public void setIncoming(BigDecimal incoming) { this.incoming = incoming; }
        public BigDecimal getOutgoing() { return outgoing; }
        public void setOutgoing(BigDecimal outgoing) { this.outgoing = outgoing; }
    }

    public static class MovementRow {
        private UUID productId;
        private String sku;
        private String productName;
        private UUID locationId;
        private String locationName;
        private String type;
        private BigDecimal quantity;
        private String referenceType;
        private String note;
        private Instant createdAt;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public String getReferenceType() { return referenceType; }
        public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    public static class ToOrderRow {
        private UUID productId;
        private String sku;
        private String productName;
        private BigDecimal minStock;
        private BigDecimal available;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getMinStock() { return minStock; }
        public void setMinStock(BigDecimal minStock) { this.minStock = minStock; }
        public BigDecimal getAvailable() { return available; }
        public void setAvailable(BigDecimal available) { this.available = available; }
    }
}
