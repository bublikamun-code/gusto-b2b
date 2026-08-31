package by.gusto.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotpVerifyRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    private String code;
}
