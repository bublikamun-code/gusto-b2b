package by.gusto.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailConfirmRequest {

    @NotBlank
    private String token;
}
