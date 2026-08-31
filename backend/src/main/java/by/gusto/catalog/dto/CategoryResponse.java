package by.gusto.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private UUID parentId;
    private Integer sort;

    @JsonProperty("isActive")
    private boolean active;

    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}
