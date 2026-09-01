package by.gusto.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "slug должен содержать только строчные латинские буквы, цифры и дефисы")
    private String slug;

    private UUID parentId;

    @NotNull
    @Builder.Default
    private Integer sort = 0;

    @Builder.Default
    private Boolean active = true;
}
