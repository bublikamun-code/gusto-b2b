package by.gusto.order.dto;

import by.gusto.order.entity.OrderEntity;
import by.gusto.order.entity.OrderItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    @Data
    public static class CreateRequest {

        /** Только для MANAGER/ADMIN: заказ от имени клиента (4.1). */
        private UUID customerCompanyId;

        /** Пусто → позиции берутся из серверной корзины пользователя. */
        private List<ItemRequest> items;

        @NotNull
        private OrderEntity.DeliveryType deliveryType;

        private String deliveryAddress;

        private String recipientName;

        private String recipientPhone;

        private String note;
    }

    @Data
    public static class ItemRequest {

        @NotNull
        private UUID productId;

        @NotNull
        @Positive
        private BigDecimal quantity;
    }

    @Data
    public static class ItemResponse {

        private UUID productId;
        private String sku;
        private String productName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal vatRate;
        private BigDecimal total;
    }

    @Data
    public static class Response {

        private UUID id;
        private String number;
        private UUID customerCompanyId;
        private UUID customerUserId;
        private UUID managerId;
        private OrderEntity.Status status;
        private OrderEntity.DeliveryType deliveryType;
        private String deliveryAddress;
        private String recipientName;
        private String recipientPhone;
        private String note;
        private BigDecimal totalAmount;
        private BigDecimal totalVat;
        private Instant createdAt;
        private List<ItemResponse> items;
    }

    @Data
    public static class CartResponse {

        private List<CartItemResponse> items;
        private BigDecimal totalAmount;
        private BigDecimal totalVat;
    }

    @Data
    public static class CartItemResponse {

        private UUID productId;
        private String sku;
        private String productName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal total;
    }

    @Data
    public static class CartItemPutRequest {

        @NotNull
        @Positive
        private BigDecimal quantity;
    }

    @Data
    public static class OrderListResponse {

        private List<Response> items;
        private long page;
        private long size;
        private long total;
    }

    public static Response toResponse(OrderEntity order, List<OrderItem> items) {
        Response response = new Response();
        response.setId(order.getId());
        response.setNumber(order.getNumber());
        response.setCustomerCompanyId(order.getCustomerCompanyId());
        response.setCustomerUserId(order.getCustomerUserId());
        response.setManagerId(order.getManagerId());
        response.setStatus(order.getStatus());
        response.setDeliveryType(order.getDeliveryType());
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setRecipientName(order.getRecipientName());
        response.setRecipientPhone(order.getRecipientPhone());
        response.setNote(order.getNote());
        response.setTotalAmount(order.getTotalAmount());
        response.setTotalVat(order.getTotalVat());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(items.stream().map(item -> {
            ItemResponse itemResponse = new ItemResponse();
            itemResponse.setProductId(item.getProductId());
            itemResponse.setSku((String) item.getProductSnapshot().get("sku"));
            itemResponse.setProductName((String) item.getProductSnapshot().get("name"));
            itemResponse.setUnit((String) item.getProductSnapshot().get("unit"));
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setVatRate(item.getVatRate());
            itemResponse.setTotal(item.getTotal());
            return itemResponse;
        }).toList());
        return response;
    }
}
