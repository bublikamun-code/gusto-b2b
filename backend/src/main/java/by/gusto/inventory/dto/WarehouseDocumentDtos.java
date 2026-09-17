package by.gusto.inventory.dto;

import by.gusto.inventory.entity.WarehouseDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WarehouseDocumentDtos {

    private WarehouseDocumentDtos() {
    }

    @Data
    public static class CreateRequest {

        @NotNull
        private WarehouseDocument.Type type;

        /** Куда приход (INCOMING). */
        private UUID locationToId;

        /** Откуда расход/списание (OUTGOING, WRITE_OFF, TRANSFER — вместе с locationToId). */
        private UUID locationFromId;

        private UUID supplierId;
        private UUID purchaseOrderId;
        private UUID customerOrderId;

        private String note;

        @NotEmpty
        @Valid
        private List<ItemRequest> items;
    }

    @Data
    public static class ItemRequest {

        @NotNull
        private UUID productId;

        @NotNull
        @Positive
        private BigDecimal quantity;

        private BigDecimal price;
    }

    @Data
    public static class Response {

        private UUID id;
        private String number;
        private WarehouseDocument.Type type;
        private WarehouseDocument.Status status;
        private UUID locationFromId;
        private UUID locationToId;
        private UUID supplierId;
        private UUID customerOrderId;
        private LocalDate documentDate;
        private String note;
        private UUID createdBy;
        private Instant createdAt;
        private Instant confirmedAt;
        private List<ItemResponse> items;
    }

    @Data
    public static class ItemResponse {

        private UUID productId;
        @NotBlank
        private String sku;
        @NotBlank
        private String productName;
        private BigDecimal quantity;
        private BigDecimal price;
    }
}
