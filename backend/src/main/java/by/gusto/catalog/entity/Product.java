package by.gusto.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String unit = "кг";

    private String manufacturer;

    @Column(nullable = false)
    @Builder.Default
    private String country = "РБ";

    @Column(name = "tnved_code")
    private String tnvedCode;

    private String barcode;

    @Column(name = "vat_rate", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal vatRate = BigDecimal.valueOf(10);

    @Column(name = "weight_per_unit", precision = 10, scale = 3)
    private BigDecimal weightPerUnit;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
