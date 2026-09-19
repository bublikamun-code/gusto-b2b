package by.gusto.waybill.dto;

import by.gusto.waybill.entity.WaybillEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaybillDtos {

    private WaybillDtos() {
    }

    @Data
    public static class CreateRequest {

        @NotNull
        private UUID orderId;

        /** ТН или ТТН (словарь 2.2). */
        @NotNull
        private WaybillEntity.Type type;

        /** Счёт, к которому привязана накладная (опционально). */
        private UUID invoiceId;

        /** Грузополучатель, если отличается от покупателя. */
        private String consigneeName;
        private String consigneeAddress;

        /** Транспорт (для ТТН, структура примера). */
        private String vehicle;
        private String driver;
        private String carrierCompany;
    }

    @Data
    public static class ItemResponse {

        private String sku;
        private String productName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal vatRate;
        private BigDecimal total;
        private BigDecimal weight;
    }

    @Data
    public static class WaybillResponse {

        private UUID id;
        private WaybillEntity.Type type;
        private String displayNumber;
        private String number;
        private String series;
        private LocalDate issueDate;
        private UUID orderId;
        private UUID invoiceId;
        private Map<String, Object> sellerSnapshot;
        private Map<String, Object> buyerSnapshot;
        private Map<String, Object> carrierSnapshot;
        private BigDecimal totalAmount;
        private BigDecimal totalVat;
        private BigDecimal totalWeight;
        private Instant createdAt;
        private List<ItemResponse> items;
    }
}
