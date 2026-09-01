package by.gusto.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyResponse {

    private UUID id;
    private String name;
    private String shortName;
    private String unp;
    private String legalAddress;
    private String actualAddress;
    private String bankAccount;
    private String bankName;
    private String bankBic;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private UUID managerId;
    private Instant createdAt;
    private Instant updatedAt;
}
