package by.gusto.ai.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Рецепт для блока «рекомендованные рецепты» и grounding для чата (S45).
 * steps / ingredients / tags — JSON в TEXT, разбор в RecipeContentParser.
 */
@Entity
@Table(name = "recipes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    /** JSON: ["шаг 1", "шаг 2", ...] */
    @Column(nullable = false)
    private String steps;

    /** JSON: [{"name":"...","sku":"...","quantity":"..."}, ...] */
    @Column(nullable = false)
    private String ingredients;

    @Column(name = "cooking_minutes")
    private Integer cookingMinutes;

    private String difficulty;

    /** JSON: ["гриль","быстро", ...] — ключи для подбора рецепта к реплике. */
    @Column(nullable = false)
    private String tags;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
