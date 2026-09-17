package by.gusto.inventory.service;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.dto.PurchaseOrderDtos.CreateRequest;
import by.gusto.inventory.dto.PurchaseOrderDtos.ItemResponse;
import by.gusto.inventory.dto.PurchaseOrderDtos.Response;
import by.gusto.inventory.entity.PurchaseOrder;
import by.gusto.inventory.entity.PurchaseOrderItem;
import by.gusto.inventory.entity.WarehouseDocumentItem;
import by.gusto.inventory.repository.PurchaseOrderItemRepository;
import by.gusto.inventory.repository.PurchaseOrderRepository;
import by.gusto.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Заказы поставщикам (S18.2): DRAFT → SENT → PARTIAL → RECEIVED (+CANCELLED).
 * Приёмка — приходным документом со ссылкой на заказ (S18.1 confirm → registerReceipt).
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Response create(CreateRequest request, UUID userId) {
        if (!supplierRepository.existsById(request.getSupplierId())) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Поставщик не найден");
        }
        BigDecimal total = request.getItems().stream()
                .map(item -> item.getQuantity().multiply(item.getPurchasePrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PurchaseOrder order = PurchaseOrder.builder()
                .number(nextNumber())
                .supplierId(request.getSupplierId())
                .status(PurchaseOrder.Status.DRAFT)
                .expectedDate(request.getExpectedDate())
                .totalAmount(total)
                .note(request.getNote())
                .createdBy(userId)
                .build();
        order = orderRepository.save(order);

        UUID orderId = order.getId();
        List<PurchaseOrderItem> items = request.getItems().stream()
                .map(item -> PurchaseOrderItem.builder()
                        .purchaseOrderId(orderId)
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .purchasePrice(item.getPurchasePrice())
                        .receivedQuantity(BigDecimal.ZERO)
                        .build())
                .toList();
        itemRepository.saveAll(items);

        return toResponse(order, items);
    }

    @Transactional
    public Response send(UUID orderId) {
        PurchaseOrder order = find(orderId);
        requireStatus(order, PurchaseOrder.Status.DRAFT, "Отправить можно только черновик");
        order.setStatus(PurchaseOrder.Status.SENT);
        orderRepository.save(order);
        return toResponse(order, itemRepository.findAllByPurchaseOrderId(orderId));
    }

    @Transactional
    public Response cancel(UUID orderId) {
        PurchaseOrder order = find(orderId);
        if (order.getStatus() != PurchaseOrder.Status.DRAFT && order.getStatus() != PurchaseOrder.Status.SENT) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Отменить можно только DRAFT/SENT, текущий статус: " + order.getStatus());
        }
        order.setStatus(PurchaseOrder.Status.CANCELLED);
        orderRepository.save(order);
        return toResponse(order, itemRepository.findAllByPurchaseOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public Response get(UUID orderId) {
        return toResponse(find(orderId), itemRepository.findAllByPurchaseOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public Page<Response> search(PurchaseOrder.Status status, UUID supplierId, int page, int size) {
        return orderRepository.search(status, supplierId, PageRequest.of(page, Math.min(size, 100)))
                .map(o -> toResponse(o, itemRepository.findAllByPurchaseOrderId(o.getId())));
    }

    /**
     * Частичный приём: вызывается при подтверждении приходного документа со ссылкой
     * на заказ. Накапливает received_quantity и переводит статус PARTIAL/RECEIVED.
     */
    @Transactional
    public void registerReceipt(UUID orderId, List<WarehouseDocumentItem> received) {
        PurchaseOrder order = find(orderId);
        if (order.getStatus() != PurchaseOrder.Status.SENT && order.getStatus() != PurchaseOrder.Status.PARTIAL) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Принять можно только отправленный заказ, текущий статус: " + order.getStatus());
        }
        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(orderId);
        Map<UUID, PurchaseOrderItem> byProduct = items.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getProductId, Function.identity()));

        boolean fullyReceived = true;
        for (WarehouseDocumentItem documentItem : received) {
            PurchaseOrderItem item = byProduct.get(documentItem.getProductId());
            if (item == null) {
                throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                        "Товар не входит в заказ поставщику");
            }
            BigDecimal newReceived = item.getReceivedQuantity().add(documentItem.getQuantity());
            if (newReceived.compareTo(item.getQuantity()) > 0) {
                throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                        "Принято больше заказанного: " + newReceived + " из " + item.getQuantity());
            }
            item.setReceivedQuantity(newReceived);
            if (newReceived.compareTo(item.getQuantity()) < 0) {
                fullyReceived = false;
            }
        }
        itemRepository.saveAll(items);
        order.setStatus(fullyReceived ? PurchaseOrder.Status.RECEIVED : PurchaseOrder.Status.PARTIAL);
        orderRepository.save(order);
    }

    private PurchaseOrder find(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ поставщику не найден"));
    }

    private void requireStatus(PurchaseOrder order, PurchaseOrder.Status expected, String message) {
        if (order.getStatus() != expected) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    message + ", текущий статус: " + order.getStatus());
        }
    }

    /** Номер вида ЗП-17 (2.2): sequence на год. */
    private String nextNumber() {
        String sequence = "doc_seq_purchase_" + Year.now().getValue();
        Long next = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
        return "ЗП-" + next;
    }

    private Response toResponse(PurchaseOrder order, List<PurchaseOrderItem> items) {
        Response response = new Response();
        response.setId(order.getId());
        response.setNumber(order.getNumber());
        response.setSupplierId(order.getSupplierId());
        supplierRepository.findById(order.getSupplierId())
                .ifPresent(supplier -> response.setSupplierName(supplier.getName()));
        response.setStatus(order.getStatus());
        response.setExpectedDate(order.getExpectedDate());
        response.setTotalAmount(order.getTotalAmount());
        response.setNote(order.getNote());
        response.setCreatedBy(order.getCreatedBy());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(items.stream().map(item -> {
            ItemResponse itemResponse = new ItemResponse();
            itemResponse.setProductId(item.getProductId());
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                itemResponse.setSku(product.getSku());
                itemResponse.setProductName(product.getName());
            });
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setPurchasePrice(item.getPurchasePrice());
            itemResponse.setReceivedQuantity(item.getReceivedQuantity());
            return itemResponse;
        }).toList());
        return response;
    }
}
