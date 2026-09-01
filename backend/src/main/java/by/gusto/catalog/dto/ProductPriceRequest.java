package by.gusto.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceRequest {

    @NotNull
    private UUID priceListId;

    @NotNull
    private UUID productId;

    @NotNull
    @Positive
    private BigDecimal price;

    @NotNull
    private LocalDate validFrom;

    private LocalDate validTo;
}
