package by.gusto.waybill.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Товарно-транспортная накладная (ТТН) и товарная накладная (ТН), S26.
 * По V1 документ создаётся окончательным снапшотом: статусов нет,
 * реквизиты и позиции фиксируются на момент выпуска, PDF сохраняется в files.
 */
@Entity
@Table(name = "waybills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaybillEntity {

    public enum Type { TN, TTN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Type type;

    /** ТН-А-1 / ТТН-А-1 (2.2); в ответах дополняется «от ДД.ММ.ГГГГ». */
    @Column(nullable = false, unique = true)
    private String number;

    private String series;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seller_snapshot", nullable = false)
    private Map<String, Object> sellerSnapshot;

    /** Покупатель/заказчик; при отличии грузополучателя — вложенный ключ consignee. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_snapshot", nullable = false)
    private Map<String, Object> buyerSnapshot;

    /** Авто, водитель, перевозчик (структура примера ТТН). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "carrier_snapshot")
    private Map<String, Object> carrierSnapshot;

    @Column(name = "pdf_file_id")
    private UUID pdfFileId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
