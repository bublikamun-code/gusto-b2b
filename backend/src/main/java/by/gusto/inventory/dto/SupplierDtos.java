package by.gusto.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    @Data
    public static class Request {

        @NotBlank
        private String name;

        private String unp;

        private String phone;

        private String email;

        private String contactPerson;

        private String note;

        private Boolean active;
    }

    @Data
    public static class Response {

        private UUID id;
        private String name;
        private String unp;
        private String phone;
        private String email;
        private String contactPerson;
        private String note;

        @JsonProperty("isActive")
        private boolean active;

        private Instant createdAt;
    }
}
