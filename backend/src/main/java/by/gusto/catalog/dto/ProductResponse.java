package by.gusto.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private UUID id;
    private String sku;
    private String name;
    private UUID categoryId;
    private UUID brandId;
    private String description;
    private String unit;
    private String manufacturer;
    private String country;
    private String tnvedCode;
    private String barcode;
    private BigDecimal vatRate;
    private BigDecimal weightPerUnit;
    private BigDecimal minStock;

    @JsonProperty("isActive")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;
}
