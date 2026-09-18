package by.gusto.catalog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Точечное управление витринными флагами товара (S19.1).
 */
@Data
public class ShowcaseFlagsRequest {

    @NotNull
    private Boolean isHit;

    @NotNull
    private Boolean isNew;
}
