package by.gusto.order.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.inventory.service.StockService;
import by.gusto.order.dto.OrderDtos;
import by.gusto.order.dto.OrderDtos.Response;
import by.gusto.order.entity.OrderEntity;
import by.gusto.order.entity.OrderEntity.Status;
import by.gusto.order.repository.OrderItemRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Жизненный цикл заказа (S22): статус-машина с правами (менеджер — по своим
 * клиентам), отмена с разблокировкой резерва (stock_balances.reserved),
 * пул «не назначено» розницы и «взять в работу» (2.7). Все действия — в audit_log.
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    /** Статус-машина: COMPLETED и CANCELLED терминальные. */
    private static final Map<Status, EnumSet<Status>> TRANSITIONS = Map.of(
            Status.NEW, EnumSet.of(Status.CONFIRMED, Status.CANCELLED),
            Status.CONFIRMED, EnumSet.of(Status.PROCESSING, Status.CANCELLED),
            Status.PROCESSING, EnumSet.of(Status.READY, Status.CANCELLED),
            Status.READY, EnumSet.of(Status.SHIPPED, Status.CANCELLED),
            Status.SHIPPED, EnumSet.of(Status.COMPLETED),
            Status.COMPLETED, EnumSet.noneOf(Status.class),
            Status.CANCELLED, EnumSet.noneOf(Status.class));

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CompanyRepository companyRepository;
    private final StockService stockService;
    private final AuditService auditService;
    private final OutboxService outboxService;

    @Transactional
    public Response changeStatus(UUID orderId, Status target, User actor) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ не найден"));
        requireCanManage(order, actor);

        Status current = order.getStatus();
        if (!TRANSITIONS.get(current).contains(target)) {
            throw new GustoException(ErrorCode.ORDER_STATUS_TRANSITION,
                    "Переход " + current + " → " + target + " недопустим");
        }

        order.setStatus(target);
        orderRepository.save(order);

        if (target == Status.CANCELLED) {
            releaseReserve(order, actor);
        }

        auditService.append(actor.getId(), "ORDER_STATUS_CHANGED", "order", orderId,
                Map.of("status", current.name()),
                Map.of("status", target.name(),
                       "number", order.getNumber(),
                       "released", target == Status.CANCELLED));

        outboxService.append("order", orderId, "ORDER_STATUS_CHANGED", Map.of(
                "orderId", orderId.toString(),
                "number", order.getNumber(),
                "from", current.name(),
                "to", target.name()));

        return OrderDtos.toResponse(order, orderItemRepository.findAllByOrderId(orderId));
    }

    /** «Взять в работу» (2.7): проставляет manager_id; розничный заказ покидает пул. */
    @Transactional
    public Response takeInWork(UUID orderId, User actor) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ не найден"));

        if (order.getManagerId() != null) {
            throw new GustoException(ErrorCode.ORDER_ALREADY_TAKEN,
                    "Заказ уже взят в работу");
        }
        if (order.getStatus() == Status.CANCELLED || order.getStatus() == Status.COMPLETED) {
            throw new GustoException(ErrorCode.ORDER_STATUS_TRANSITION,
                    "Заказ в терминальном статусе " + order.getStatus());
        }

        order.setManagerId(actor.getId());
        orderRepository.save(order);

        auditService.append(actor.getId(), "ORDER_TAKE", "order", orderId,
                Map.of("managerId", ""),
                Map.of("managerId", actor.getId().toString(), "number", order.getNumber()));

        return OrderDtos.toResponse(order, orderItemRepository.findAllByOrderId(orderId));
    }

    /**
     * GET /manager/orders: свои (scope=mine) + пул «не назначено» (scope=unassigned);
     * ADMIN может scope=all. Фильтр по статусу опционален.
     */
    @Transactional(readOnly = true)
    public Page<Response> managerList(User actor, String scope, Status status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        boolean isManager = actor.getRole() == Role.MANAGER;
        UUID actorId = actor.getId();

        String effective = scope == null || scope.isBlank() ? "mine" : scope;
        if (isManager && "all".equals(effective)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Менеджеру доступны scope=mine|unassigned");
        }

        Page<OrderEntity> orders = switch (effective) {
            case "mine" -> status == null
                    ? orderRepository.findAllByManagerIdOrderByCreatedAtDesc(actorId, pageable)
                    : orderRepository.findAllByManagerIdAndStatusOrderByCreatedAtDesc(actorId, status, pageable);
            case "unassigned" -> status == null
                    ? orderRepository.findAllByManagerIdIsNullOrderByCreatedAtDesc(pageable)
                    : orderRepository.findAllByManagerIdIsNullAndStatusOrderByCreatedAtDesc(status, pageable);
            case "all" -> status == null
                    ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : orderRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
            default -> throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "scope: mine|unassigned" + (isManager ? "" : "|all"));
        };
        return orders.map(o -> OrderDtos.toResponse(o, orderItemRepository.findAllByOrderId(o.getId())));
    }

    /**
     * Права на смену статуса: ADMIN — любые; MANAGER — назначенные ему заказы
     * и заказы компаний, закреплённых за ним (2.1 «менеджер по своим клиентам»).
     */
    private void requireCanManage(OrderEntity order, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() != Role.MANAGER) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
        boolean assigned = actor.getId().equals(order.getManagerId());
        boolean ownClient = order.getCustomerCompanyId() != null
                && companyRepository.findByIdAndManagerId(order.getCustomerCompanyId(), actor.getId()).isPresent();
        if (!assigned && !ownClient) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** Отмена разблокирует резерв со склада, с которого резервировали (S20/S22). */
    private void releaseReserve(OrderEntity order, User actor) {
        orderItemRepository.findAllByOrderId(order.getId()).forEach(item ->
                stockService.release(item.getProductId(), order.getStockLocationId(), item.getQuantity(),
                        "ORDER", order.getId(), actor.getId()));
    }
}
