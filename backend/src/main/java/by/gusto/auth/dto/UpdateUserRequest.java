package by.gusto.auth.dto;

import by.gusto.auth.entity.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UpdateUserRequest {

    @Size(min = 2, max = 255)
    private String fullName;

    @Size(max = 50)
    private String phone;

    private Role role;

    private UUID companyId;

    @JsonProperty("isActive")
    private Boolean active;
}
