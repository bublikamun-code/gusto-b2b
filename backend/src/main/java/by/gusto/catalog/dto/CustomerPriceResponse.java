package by.gusto.catalog.dto;

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
public class CustomerPriceResponse {

    private UUID id;
    private UUID companyId;
    private UUID productId;
    private BigDecimal price;
    private LocalDate validFrom;
    private LocalDate validTo;
}
