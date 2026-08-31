package by.gusto.company.dto;

import by.gusto.company.validation.Unp;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class CreateCompanyRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String shortName;

    @Unp
    private String unp;

    @Size(max = 500)
    private String legalAddress;

    @Size(max = 500)
    private String actualAddress;

    @Size(max = 50)
    private String bankAccount;

    @Size(max = 255)
    private String bankName;

    @Size(max = 20)
    private String bankBic;

    @Size(max = 50)
    private String contactPhone;

    @Email
    @Size(max = 255)
    private String contactEmail;

    private UUID managerId;
}
