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
public class CustomerDiscountResponse {

    private UUID id;
    private UUID companyId;
    private UUID brandId;
    private UUID categoryId;
    private BigDecimal discountPercent;
    private LocalDate validFrom;
    private LocalDate validTo;
}
