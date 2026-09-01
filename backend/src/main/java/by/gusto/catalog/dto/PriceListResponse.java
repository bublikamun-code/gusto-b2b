package by.gusto.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListResponse {

    private UUID id;
    private String name;
    private LocalDate validFrom;
    private LocalDate validTo;

    @JsonProperty("isActive")
    private boolean active;
}
