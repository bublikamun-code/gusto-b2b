package by.gusto.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailResendRequest {

    @NotBlank
    @Email
    private String email;
}
