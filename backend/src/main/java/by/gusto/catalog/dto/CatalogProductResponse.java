package by.gusto.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogProductResponse {

    private UUID id;
    private String sku;
    private String name;
    private CategoryResponse category;
    private BrandResponse brand;
    private String unit;
    private String description;
    private BigDecimal retailPrice;

    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();
}
