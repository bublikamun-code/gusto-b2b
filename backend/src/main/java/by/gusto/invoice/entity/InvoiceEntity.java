package by.gusto.invoice.entity;

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

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceEntity {

    public enum Status { DRAFT, ISSUED, PARTIALLY_PAID, PAID, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    /** СЧ-<N>; в ответах API дополняется «от ДД.ММ.ГГГГ» (2.2). */
    @Column(nullable = false, unique = true)
    private String number;

    private String series;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_company_id")
    private UUID customerCompanyId;

    /** Снапшот реквизитов продавца из settings('seller.requisites') (2.4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seller_snapshot", nullable = false)
    private Map<String, Object> sellerSnapshot;

    /** Снапшот реквизитов покупателя из компании на момент счёта. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_snapshot", nullable = false)
    private Map<String, Object> buyerSnapshot;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "pdf_file_id")
    private UUID pdfFileId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
