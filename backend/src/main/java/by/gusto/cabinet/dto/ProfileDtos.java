package by.gusto.cabinet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    @Data
    public static class UpdateProfileRequest {

        @NotBlank
        @Size(min = 2, max = 255)
        private String fullName;

        private String phone;
    }

    @Data
    public static class ProfileResponse {

        private String email;
        private String fullName;
        private String phone;
        private String role;
    }

    @Data
    public static class ChangePasswordRequest {

        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 128)
        private String newPassword;
    }
}
