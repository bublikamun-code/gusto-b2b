package by.gusto.inventory.service;

import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.inventory.dto.WarehouseDocumentDtos.CreateRequest;
import by.gusto.inventory.dto.WarehouseDocumentDtos.ItemResponse;
import by.gusto.inventory.dto.WarehouseDocumentDtos.Response;
import by.gusto.inventory.entity.StockMovement;
import by.gusto.inventory.entity.WarehouseDocument;
import by.gusto.inventory.entity.WarehouseDocumentItem;
import by.gusto.inventory.repository.WarehouseDocumentItemRepository;
import by.gusto.inventory.repository.WarehouseDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Складские документы (S18.1): приход / расход / списание.
 * Подтверждение документа — ЕДИНСТВЕННАЯ точка создания движений.
 */
@Service
@RequiredArgsConstructor
public class WarehouseDocumentService {

    private static final Map<WarehouseDocument.Type, String> NUMBER_PREFIXES = Map.of(
            WarehouseDocument.Type.INCOMING, "ПН",
            WarehouseDocument.Type.OUTGOING, "РС",
            WarehouseDocument.Type.WRITE_OFF, "СП",
            WarehouseDocument.Type.TRANSFER, "ПР",
            WarehouseDocument.Type.INVENTORY, "ИН");

    private final WarehouseDocumentRepository documentRepository;
    private final WarehouseDocumentItemRepository itemRepository;
    private final StockService stockService;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Response create(CreateRequest request, UUID userId) {
        validateLocations(request);

        WarehouseDocument document = WarehouseDocument.builder()
                .number(nextNumber(request.getType()))
                .type(request.getType())
                .status(WarehouseDocument.Status.DRAFT)
                .locationFromId(request.getLocationFromId())
                .locationToId(request.getLocationToId())
                .supplierId(request.getSupplierId())
                .purchaseOrderId(request.getPurchaseOrderId())
                .customerOrderId(request.getCustomerOrderId())
                .documentDate(LocalDate.now())
                .note(request.getNote())
                .createdBy(userId)
                .build();
        document = documentRepository.save(document);

        UUID documentId = document.getId();
        List<WarehouseDocumentItem> items = request.getItems().stream()
                .map(item -> WarehouseDocumentItem.builder()
                        .documentId(documentId)
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();
        itemRepository.saveAll(items);

        return toResponse(document, items);
    }

    /** Подтверждение: создаёт движения по всем позициям в одной транзакции. */
    @Transactional
    public Response confirm(UUID documentId, UUID userId) {
        WarehouseDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Документ не найден"));
        if (document.getStatus() != WarehouseDocument.Status.DRAFT) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Подтвердить можно только черновик, текущий статус: " + document.getStatus());
        }
        List<WarehouseDocumentItem> items = itemRepository.findAllByDocumentId(documentId);
        for (WarehouseDocumentItem item : items) {
            UUID locationId = switch (document.getType()) {
                case INCOMING -> document.getLocationToId();
                case OUTGOING, WRITE_OFF -> document.getLocationFromId();
                default -> throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                        "Тип документа обрабатывается в S18.3: " + document.getType());
            };
            StockMovement.Type movementType = switch (document.getType()) {
                case INCOMING -> StockMovement.Type.INCOMING;
                case OUTGOING, WRITE_OFF -> StockMovement.Type.OUTGOING;
                default -> throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID);
            };
            stockService.applyMovement(item.getProductId(), locationId, movementType, item.getQuantity(),
                    "WAREHOUSE_DOCUMENT", documentId, document.getNumber(), userId);
        }
        document.setStatus(WarehouseDocument.Status.CONFIRMED);
        document.setConfirmedAt(Instant.now());
        documentRepository.save(document);
        return toResponse(document, items);
    }

    /** Отмена черновика; отмена CONFIRMED запрещена — только сторно-документом (S18.3). */
    @Transactional
    public Response cancel(UUID documentId) {
        WarehouseDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Документ не найден"));
        if (document.getStatus() != WarehouseDocument.Status.DRAFT) {
            throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Отменить можно только черновик; подтверждённый документ закрывается сторно");
        }
        document.setStatus(WarehouseDocument.Status.CANCELLED);
        documentRepository.save(document);
        return toResponse(document, itemRepository.findAllByDocumentId(documentId));
    }

    @Transactional(readOnly = true)
    public Response get(UUID documentId) {
        WarehouseDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Документ не найден"));
        return toResponse(document, itemRepository.findAllByDocumentId(documentId));
    }

    @Transactional(readOnly = true)
    public Page<Response> search(WarehouseDocument.Type type, WarehouseDocument.Status status,
                                 UUID locationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<WarehouseDocument> documents = documentRepository.search(type, status, locationId, pageable);
        return documents.map(d -> toResponse(d, itemRepository.findAllByDocumentId(d.getId())));
    }

    /** Списание — это тоже OUTGOING-движение, но заметка сохраняет тип документа. */
    private void validateLocations(CreateRequest request) {
        switch (request.getType()) {
            case INCOMING -> requireLocation(request.getLocationToId(), "locationToId обязателен для прихода");
            case OUTGOING, WRITE_OFF ->
                    requireLocation(request.getLocationFromId(), "locationFromId обязателен для расхода/списания");
            case TRANSFER, INVENTORY -> throw new GustoException(ErrorCode.STOCK_DOCUMENT_INVALID,
                    "Тип документа вводится в S18.3: " + request.getType());
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Документ обязан иметь хотя бы одну позицию");
        }
        boolean badQuantity = request.getItems().stream()
                .anyMatch(item -> item.getQuantity() == null
                        || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0);
        if (badQuantity) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Количество позиции должно быть положительным");
        }
    }

    private void requireLocation(UUID locationId, String message) {
        if (locationId == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    /** Номер вида ПН-17 (2.2): sequence на (тип, год), max(number)+1 запрещён. */
    private String nextNumber(WarehouseDocument.Type type) {
        int year = Year.now().getValue();
        String sequence = "doc_seq_warehouse_" + type.name().toLowerCase() + "_" + year;
        Long next = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
        return NUMBER_PREFIXES.get(type) + "-" + next;
    }

    private Response toResponse(WarehouseDocument document, List<WarehouseDocumentItem> items) {
        Map<UUID, by.gusto.catalog.entity.Product> products = items.isEmpty()
                ? Map.of()
                : productRepository.findAllById(items.stream().map(WarehouseDocumentItem::getProductId).toList())
                        .stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));

        Response response = new Response();
        response.setId(document.getId());
        response.setNumber(document.getNumber());
        response.setType(document.getType());
        response.setStatus(document.getStatus());
        response.setLocationFromId(document.getLocationFromId());
        response.setLocationToId(document.getLocationToId());
        response.setSupplierId(document.getSupplierId());
        response.setCustomerOrderId(document.getCustomerOrderId());
        response.setDocumentDate(document.getDocumentDate());
        response.setNote(document.getNote());
        response.setCreatedBy(document.getCreatedBy());
        response.setCreatedAt(document.getCreatedAt());
        response.setConfirmedAt(document.getConfirmedAt());
        response.setItems(items.stream().map(item -> {
            ItemResponse itemResponse = new ItemResponse();
            itemResponse.setProductId(item.getProductId());
            by.gusto.catalog.entity.Product product = products.get(item.getProductId());
            itemResponse.setSku(product != null ? product.getSku() : null);
            itemResponse.setProductName(product != null ? product.getName() : null);
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setPrice(item.getPrice());
            return itemResponse;
        }).toList());
        return response;
    }
}
