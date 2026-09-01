package by.gusto.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListRequest {

    @NotBlank
    private String name;

    @NotNull
    private LocalDate validFrom;

    private LocalDate validTo;

    @JsonProperty("isActive")
    @Builder.Default
    private Boolean active = true;
}
