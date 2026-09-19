package by.gusto.integration.entity;

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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Файл обмена с 1С (S35/S36): статусы по словарю 2.8. */
@Entity
@Table(name = "integration_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationFileEntity {

    public enum Direction { IMPORT, EXPORT }

    public enum Type { PRICES, STOCK, ORDERS, INVOICES, WAYBILLS }

    public enum Status { UPLOADED, PROCESSING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.UPLOADED;

    @Column(name = "rows_total")
    private Integer rowsTotal;

    @Column(name = "rows_ok")
    private Integer rowsOk;

    @Column(name = "rows_error")
    private Integer rowsError;

    @Column(name = "error_log_file_id")
    private UUID errorLogFileId;

    @Column(name = "processed_by")
    private UUID processedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
