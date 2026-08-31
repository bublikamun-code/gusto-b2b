package by.gusto.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank
    @Size(max = 100)
    private String sku;

    @NotBlank
    @Size(max = 500)
    private String name;

    @NotNull
    private UUID categoryId;

    private UUID brandId;

    @Size(max = 4000)
    private String description;

    @Size(max = 20)
    @Builder.Default
    private String unit = "кг";

    @Size(max = 255)
    private String manufacturer;

    @Size(max = 100)
    @Builder.Default
    private String country = "РБ";

    @Size(max = 20)
    private String tnvedCode;

    @Size(max = 50)
    private String barcode;

    @Builder.Default
    private BigDecimal vatRate = BigDecimal.valueOf(10);

    private BigDecimal weightPerUnit;

    @Builder.Default
    private BigDecimal minStock = BigDecimal.ZERO;

    @JsonProperty("isActive")
    @Builder.Default
    private Boolean active = true;
}
