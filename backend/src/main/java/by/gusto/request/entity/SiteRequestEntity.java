package by.gusto.request.entity;

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

/** Заявка с сайта (S31): публичная форма, словари 2.8; конвертация в лид — при создании. */
@Entity
@Table(name = "site_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteRequestEntity {

    public enum Type { CALLBACK, WHOLESALE, RETAIL, OTHER }

    public enum Status { NEW, IN_PROGRESS, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;

    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.NEW;

    /** Лид, созданный из заявки (пул «не назначено», 2.7). */
    @Column(name = "lead_id")
    private UUID leadId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
