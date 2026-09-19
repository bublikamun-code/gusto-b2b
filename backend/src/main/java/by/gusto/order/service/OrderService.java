package by.gusto.order.service;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.service.StockService;
import by.gusto.order.dto.OrderDtos.CartItemResponse;
import by.gusto.order.dto.OrderDtos.CartResponse;
import by.gusto.order.dto.OrderDtos.CreateRequest;
import by.gusto.order.dto.OrderDtos.ItemRequest;
import by.gusto.order.dto.OrderDtos.Response;
import by.gusto.order.entity.OrderEntity;
import by.gusto.order.entity.OrderItem;
import by.gusto.order.repository.OrderItemRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Создание заказа (S20). Транзакция: склад по умолчанию (1.6) → проверка и резерв
 * остатков (FOR UPDATE) → снапшот цен (2.3, 2.5) → заказ с номером по sequence (2.2).
 * Розница физлица создаётся без manager_id — пул «не назначено» (2.7).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final StockService stockService;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Response create(CreateRequest request, User user) {
        validateRequest(request, user);

        UUID companyId = resolveCompanyId(request, user);
        boolean retail = companyId == null;

        // Позиции: из тела запроса или из серверной корзины
        boolean fromCart = request.getItems() == null || request.getItems().isEmpty();
        Map<UUID, BigDecimal> quantities = fromCart
                ? cartQuantities(user.getId(), companyId)
                : request.getItems().stream()
                        .collect(Collectors.toMap(ItemRequest::getProductId, ItemRequest::getQuantity,
                                BigDecimal::add, LinkedHashMap::new));
        if (quantities.isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Заказ пуст: нет позиций и корзина пуста");
        }

        // Снапшот цен и расчёт сумм (2.3: НДС-включённые цены)
        Map<UUID, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        UUID locationId = stockService.defaultLocationId();
        OrderEntity order = OrderEntity.builder()
                .customerCompanyId(companyId)
                .customerUserId(user.getId())
                .status(OrderEntity.Status.NEW)
                .deliveryType(request.getDeliveryType())
                .deliveryAddress(request.getDeliveryAddress())
                .recipientName(request.getRecipientName())
                .recipientPhone(request.getRecipientPhone())
                .stockLocationId(locationId)
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .totalVat(BigDecimal.ZERO)
                .build();
        order.setNumber(nextNumber());
        order.setTotalAmount(BigDecimal.ZERO);
        order = orderRepository.save(order);

        for (Map.Entry<UUID, BigDecimal> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null || !product.isActive() || product.getDeletedAt() != null) {
                throw new GustoException(ErrorCode.NOT_FOUND, "Товар недоступен: " + entry.getKey());
            }
            BigDecimal price = cartService.resolvePrice(user.getId(), companyId, product);
            BigDecimal lineTotal = price.multiply(entry.getValue()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = CartService.vatIncluded(product.getVatRate(), lineTotal);
            totalAmount = totalAmount.add(lineTotal);
            totalVat = totalVat.add(lineVat);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("sku", product.getSku());
            snapshot.put("name", product.getName());
            snapshot.put("unit", product.getUnit());
            if (product.getTnvedCode() != null) {
                snapshot.put("tnved", product.getTnvedCode());
            }

            orderItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .productSnapshot(snapshot)
                    .quantity(entry.getValue())
                    .unitPrice(price)
                    .vatRate(product.getVatRate())
                    .total(lineTotal)
                    .build());
        }

        // Резерв со склада по умолчанию (1.6): STOCK_INSUFFICIENT откатывает всю транзакцию,
        // заказ и позиции не сохраняются
        for (OrderItem item : orderItems) {
            stockService.reserve(item.getProductId(), locationId, item.getQuantity(),
                    "ORDER", order.getId(), user.getId());
        }

        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        order.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        order = orderRepository.save(order);

        UUID orderId = order.getId();
        orderItems.forEach(item -> item.setOrderId(orderId));
        orderItemRepository.saveAll(orderItems);

        if (fromCart) {
            cartService.clear(user.getId());
        }

        // Событие для уведомлений (2.6): доставка — outbox, отправка — поллер S31
        outboxService.append("order", orderId, "ORDER_CREATED", Map.of(
                "orderId", orderId.toString(),
                "number", order.getNumber(),
                "retail", retail,
                "companyId", companyId == null ? "" : companyId.toString(),
                "totalAmount", order.getTotalAmount().toPlainString()));

        return toResponse(order, orderItems);
    }

    @Transactional(readOnly = true)
    public Response getById(UUID orderId, User user) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ не найден"));
        checkAccess(order, user);
        return toResponse(order, orderItemRepository.findAllByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public List<Response> listVisible(User user, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<OrderEntity> orders;
        if (user.getRole() == Role.CUSTOMER_LEGAL && user.getCompanyId() != null) {
            orders = orderRepository.findAllByCustomerCompanyIdOrderByCreatedAtDesc(user.getCompanyId(), pageable);
        } else if (user.getRole() == Role.CUSTOMER_INDIVIDUAL) {
            orders = orderRepository.findAllByCustomerUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        } else if (user.getRole() == Role.ACCOUNTANT) {
            // бухгалтер работает с документами по всем заказам (матрица 2.1, S27)
            orders = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            // MANAGER/ADMIN: свои + пул (2.7); расширенные фильтры — S22
            orders = orderRepository.findAllVisibleTo(user.getId(), pageable);
        }
        return orders.map(o -> toResponse(o, orderItemRepository.findAllByOrderId(o.getId()))).getContent();
    }

    private void checkAccess(OrderEntity order, User user) {
        boolean staff = user.getRole() == Role.ADMIN
                || user.getRole() == Role.MANAGER
                || user.getRole() == Role.ACCOUNTANT;
        boolean own = order.getCustomerUserId().equals(user.getId())
                || (user.getCompanyId() != null && user.getCompanyId().equals(order.getCustomerCompanyId()));
        if (!staff && !own) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Map<UUID, BigDecimal> cartQuantities(UUID userId, UUID companyId) {
        CartResponse cart = cartService.getCart(userId, companyId);
        return cart.getItems().stream()
                .collect(Collectors.toMap(CartItemResponse::getProductId, CartItemResponse::getQuantity,
                        BigDecimal::add, LinkedHashMap::new));
    }

    private UUID resolveCompanyId(CreateRequest request, User user) {
        switch (user.getRole()) {
            case CUSTOMER_LEGAL -> {
                if (user.getCompanyId() == null) {
                    throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
                }
                return user.getCompanyId();
            }
            case CUSTOMER_INDIVIDUAL -> {
                return null;
            }
            case MANAGER, ADMIN -> {
                if (request.getCustomerCompanyId() == null) {
                    throw new GustoException(ErrorCode.VALIDATION_FAILED,
                            "Укажите customerCompanyId: заказ от имени клиента");
                }
                // менеджер всегда передаёт позиции явно
                if (request.getItems() == null || request.getItems().isEmpty()) {
                    throw new GustoException(ErrorCode.VALIDATION_FAILED,
                            "Заказ от имени клиента создаётся с явными позициями");
                }
                return request.getCustomerCompanyId();
            }
            default -> throw new GustoException(ErrorCode.ACCESS_DENIED,
                    "Роль не может создавать заказы");
        }
    }

    private void validateRequest(CreateRequest request, User user) {
        boolean retail = user.getRole() == Role.CUSTOMER_INDIVIDUAL;
        // Контакты получателя: для розницы обязательны, для юрлица — при доставке (S20)
        if (retail) {
            if (isBlank(request.getRecipientName()) || isBlank(request.getRecipientPhone())) {
                throw new GustoException(ErrorCode.VALIDATION_FAILED,
                        "Для розничного заказа укажите имя и телефон получателя");
            }
        }
        if (request.getDeliveryType() == OrderEntity.DeliveryType.DELIVERY) {
            if (isBlank(request.getDeliveryAddress())) {
                throw new GustoException(ErrorCode.VALIDATION_FAILED, "Укажите адрес доставки");
            }
            if (!retail && (isBlank(request.getRecipientName()) || isBlank(request.getRecipientPhone()))) {
                throw new GustoException(ErrorCode.VALIDATION_FAILED,
                        "Для доставки укажите имя и телефон получателя");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Номер З-2026-00042 (2.2): sequence на год, max(number)+1 запрещён. */
    private String nextNumber() {
        int year = Year.now().getValue();
        long next = jdbcTemplate.queryForObject("select nextval('order_seq_" + year + "')", Long.class);
        return String.format("З-%d-%05d", year, next);
    }

    private Response toResponse(OrderEntity order, List<OrderItem> items) {
        return by.gusto.order.dto.OrderDtos.toResponse(order, items);
    }
}
