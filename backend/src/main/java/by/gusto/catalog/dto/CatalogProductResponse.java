package by.gusto.catalog.dto;

import by.gusto.inventory.dto.StockStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private StockStatus stockStatus;

    @JsonProperty("isHit")
    private boolean hit;

    @JsonProperty("isNew")
    private boolean newProduct;

    /** Шаг количества весового товара (из products.weight_per_unit), null — без шага. */
    private BigDecimal weightStep;

    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    public String getImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }
}
