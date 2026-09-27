package by.gusto.request.dto;

import by.gusto.request.entity.SiteRequestEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public final class SiteRequestDtos {

    private SiteRequestDtos() {
    }

    /** Публичная форма «Стать клиентом»/обратной связи (S31/S34). */
    @Data
    public static class CreateRequest {

        // Форма публичная и без капчи, поэтому границы обязательны:
        // иначе в заявку уходили мегабайты текста от анонимного клиента (S44).
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 32)
        private String phone;

        @Email
        @Size(max = 254)
        private String email;

        @NotNull
        private SiteRequestEntity.Type type;

        @Size(max = 4000)
        private String message;
    }

    public record SiteRequestResponse(
            UUID id, String name, String phone, String email, String message,
            String type, String status, UUID leadId, Instant createdAt) {
    }
}
