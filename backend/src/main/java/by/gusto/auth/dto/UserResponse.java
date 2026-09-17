package by.gusto.auth.dto;

import by.gusto.auth.entity.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;

    private UUID companyId;

    @JsonProperty("isActive")
    private boolean active;

    @JsonProperty("totpEnabled")
    private boolean totpEnabled;

    /** Подтверждён ли email. Требование действует только для саморегистрации физлиц (S08.1). */
    @JsonProperty("emailConfirmed")
    private boolean emailConfirmed;

    private Instant createdAt;
    private Instant updatedAt;
}
