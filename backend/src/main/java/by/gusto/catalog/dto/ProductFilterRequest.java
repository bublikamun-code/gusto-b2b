package by.gusto.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFilterRequest {

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 20;

    private String search;
    private UUID categoryId;
    private UUID brandId;
}
