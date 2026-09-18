package by.gusto.invoice.dto;

import by.gusto.invoice.entity.InvoiceEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** Создание счёта из заказа. */
    @Data
    public static class CreateRequest {

        @NotNull
        private UUID orderId;
    }

    @Data
    public static class ItemResponse {

        private String sku;
        private String productName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal vatRate;
        private BigDecimal vat;
        private BigDecimal total;
    }

    @Data
    public static class InvoiceResponse {

        private UUID id;
        /** «СЧ-N от ДД.ММ.ГГГГ» (2.2). */
        private String displayNumber;
        private String number;
        private LocalDate issueDate;
        private UUID orderId;
        private UUID customerCompanyId;
        private Map<String, Object> sellerSnapshot;
        private Map<String, Object> buyerSnapshot;
        private BigDecimal totalAmount;
        private BigDecimal totalVat;
        private InvoiceEntity.Status status;
        private Instant createdAt;
        private List<ItemResponse> items;
    }
}
