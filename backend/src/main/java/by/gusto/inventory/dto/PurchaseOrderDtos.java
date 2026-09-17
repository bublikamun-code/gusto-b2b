package by.gusto.inventory.dto;

import by.gusto.inventory.entity.PurchaseOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    @Data
    public static class CreateRequest {

        @NotNull
        private UUID supplierId;

        private LocalDate expectedDate;

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

        @NotNull
        @Positive
        private BigDecimal purchasePrice;
    }

    @Data
    public static class Response {

        private UUID id;
        private String number;
        private UUID supplierId;
        private String supplierName;
        private PurchaseOrder.Status status;
        private LocalDate expectedDate;
        private BigDecimal totalAmount;
        private String note;
        private UUID createdBy;
        private Instant createdAt;
        private List<ItemResponse> items;
    }

    @Data
    public static class ItemResponse {

        private UUID productId;
        private String sku;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal purchasePrice;
        private BigDecimal receivedQuantity;
    }
}
