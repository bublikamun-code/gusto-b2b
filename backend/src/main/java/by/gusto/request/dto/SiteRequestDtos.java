package by.gusto.request.dto;

import by.gusto.request.entity.SiteRequestEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public final class SiteRequestDtos {

    private SiteRequestDtos() {
    }

    /** Публичная форма «Стать клиентом»/обратной связи (S31/S34). */
    @Data
    public static class CreateRequest {

        @NotBlank
        private String name;

        private String phone;
        private String email;

        @NotNull
        private SiteRequestEntity.Type type;

        private String message;
    }

    public record SiteRequestResponse(
            UUID id, String name, String phone, String email, String message,
            String type, String status, UUID leadId, Instant createdAt) {
    }
}
