package by.gusto.waybill.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.common.pdf.BrandPdfSupport;
import by.gusto.common.settings.SettingsService;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.service.FileStorageService;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.repository.InvoiceRepository;
import by.gusto.order.entity.OrderEntity;
import by.gusto.order.entity.OrderItem;
import by.gusto.order.repository.OrderItemRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.waybill.dto.WaybillDtos.CreateRequest;
import by.gusto.waybill.dto.WaybillDtos.ItemResponse;
import by.gusto.waybill.dto.WaybillDtos.WaybillResponse;
import by.gusto.waybill.entity.WaybillEntity;
import by.gusto.waybill.entity.WaybillItem;
import by.gusto.waybill.repository.WaybillItemRepository;
import by.gusto.waybill.repository.WaybillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ТН/ТТН (S26): накладная создаётся из заказа окончательным снапшотом
 * (статусов нет по V1): реквизиты сторон, транспорт (ТТН), позиции с массой,
 * НДС расчётно (2.3); PDF формируется сразу и хранится как PRIVATE (1.6).
 * Нумерация ТН-<серия>-N / ТТН-<серия>-N по sequence (2.2).
 */
@Service
@RequiredArgsConstructor
public class WaybillService {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final WaybillRepository waybillRepository;
    private final WaybillItemRepository waybillItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final BrandPdfSupport brand;
    private final WaybillPdfRenderer renderer;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public WaybillResponse create(CreateRequest request, User actor) {
        OrderEntity order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ не найден"));
        if (order.getStatus() == OrderEntity.Status.CANCELLED) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Заказ отменён — накладная не оформляется");
        }
        if (order.getCustomerCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Накладная оформляется только на заказ юрлица");
        }
        if (request.getInvoiceId() != null) {
            InvoiceEntity invoice = invoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Счёт не найден"));
            if (!invoice.getOrderId().equals(order.getId())) {
                throw new GustoException(ErrorCode.VALIDATION_FAILED, "Счёт относится к другому заказу");
            }
        }

        Company company = companyRepository.findById(order.getCustomerCompanyId())
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Компания клиента не найдена"));
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Заказ без позиций");
        }

        String series = series(request.getType());

        WaybillEntity waybill = WaybillEntity.builder()
                .type(request.getType())
                .series(series)
                .issueDate(LocalDate.now())
                .orderId(order.getId())
                .invoiceId(request.getInvoiceId())
                .sellerSnapshot(sellerSnapshot())
                .buyerSnapshot(buyerSnapshot(company, request))
                .carrierSnapshot(carrierSnapshot(request))
                .createdBy(actor.getId())
                .build();
        waybill.setNumber(nextNumber(request.getType(), series));
        waybill = waybillRepository.save(waybill);

        List<WaybillItem> items = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            items.add(WaybillItem.builder()
                    .waybillId(waybill.getId())
                    .productSnapshot(new LinkedHashMap<>(orderItem.getProductSnapshot()))
                    .quantity(orderItem.getQuantity())
                    .unitPrice(orderItem.getUnitPrice())
                    .vatRate(orderItem.getVatRate())
                    .total(orderItem.getTotal().setScale(2, RoundingMode.HALF_UP))
                    .weight(weightOf(orderItem))
                    .build());
        }
        waybillItemRepository.saveAll(items);

        // Снапшот зафиксирован — сразу формируем PDF (S25/S26 общий рендер-подход)
        byte[] pdf = renderer.render(waybill, items);
        String storageKey = UUID.randomUUID().toString();
        fileStorageService.store(new ByteArrayInputStream(pdf), storageKey);
        FileEntity file = fileRepository.save(FileEntity.builder()
                .storageKey(storageKey)
                .originalName(waybill.getNumber() + ".pdf")
                .mimeType("application/pdf")
                .sizeBytes((long) pdf.length)
                .ownerId(actor.getId())
                .visibility(FileEntity.Visibility.PRIVATE)
                .build());
        waybill.setPdfFileId(file.getId());
        waybill = waybillRepository.save(waybill);

        auditService.append(actor.getId(), "WAYBILL_CREATE", "waybill", waybill.getId(),
                null,
                Map.of("number", waybill.getNumber(), "orderId", order.getId().toString(),
                        "type", waybill.getType().name()));

        return toResponse(waybill, items);
    }

    @Transactional(readOnly = true)
    public WaybillResponse getById(UUID waybillId, User actor) {
        WaybillEntity waybill = loadWaybill(waybillId);
        requireCanView(waybill, actor);
        return toResponse(waybill, waybillItemRepository.findAllByWaybillId(waybillId));
    }

    @Transactional(readOnly = true)
    public Page<WaybillResponse> list(User actor, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<WaybillEntity> waybills = actor.getRole() == Role.MANAGER
                ? waybillRepository.findAllVisibleTo(actor.getId(), pageable)
                : waybillRepository.findAllByOrderByCreatedAtDesc(pageable);
        return waybills.map(w -> toResponse(w, waybillItemRepository.findAllByWaybillId(w.getId())));
    }

    /** Кабинет юрлица: накладные заказов своей компании (2.1). */
    @Transactional(readOnly = true)
    public Page<WaybillResponse> listForCompany(UUID companyId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return waybillRepository.findAllByCompanyOrderByCreatedAtDesc(companyId, pageable)
                .map(w -> toResponse(w, waybillItemRepository.findAllByWaybillId(w.getId())));
    }

    @Transactional
    public FileEntity ensurePdf(UUID waybillId, UUID ownerId) {
        WaybillEntity waybill = waybillRepository.findById(waybillId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Накладная не найдена"));
        if (waybill.getPdfFileId() != null) {
            return fileRepository.findById(waybill.getPdfFileId())
                    .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "PDF-файл накладной утерян"));
        }
        byte[] pdf = renderer.render(waybill, waybillItemRepository.findAllByWaybillId(waybillId));
        String storageKey = UUID.randomUUID().toString();
        fileStorageService.store(new ByteArrayInputStream(pdf), storageKey);
        FileEntity file = fileRepository.save(FileEntity.builder()
                .storageKey(storageKey)
                .originalName(waybill.getNumber() + ".pdf")
                .mimeType("application/pdf")
                .sizeBytes((long) pdf.length)
                .ownerId(ownerId)
                .visibility(FileEntity.Visibility.PRIVATE)
                .build());
        waybill.setPdfFileId(file.getId());
        waybillRepository.save(waybill);
        return file;
    }

    @Transactional(readOnly = true)
    public java.io.InputStream load(FileEntity file) {
        return fileStorageService.load(file.getStorageKey());
    }

    // ----- internals -------------------------------------------------------------

    private WaybillEntity loadWaybill(UUID waybillId) {
        return waybillRepository.findById(waybillId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Накладная не найдена"));
    }

    private void requireCanView(WaybillEntity waybill, User actor) {
        boolean staff = actor.getRole() == Role.ADMIN || actor.getRole() == Role.ACCOUNTANT
                || actor.getRole() == Role.MANAGER;
        if (staff) {
            return;
        }
        OrderEntity order = orderRepository.findById(waybill.getOrderId()).orElse(null);
        boolean ownCompany = order != null && actor.getCompanyId() != null
                && actor.getCompanyId().equals(order.getCustomerCompanyId());
        if (!ownCompany) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** Масса позиции: количество × вес единицы товара (пример ТТН); без веса — null. */
    private BigDecimal weightOf(OrderItem orderItem) {
        Product product = productRepository.findById(orderItem.getProductId()).orElse(null);
        if (product == null || product.getWeightPerUnit() == null) {
            return null;
        }
        return orderItem.getQuantity().multiply(product.getWeightPerUnit())
                .setScale(3, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sellerSnapshot() {
        String json = settingsService.getString("seller.requisites");
        if (json == null) {
            throw new GustoException(ErrorCode.INTERNAL, "Не заданы реквизиты продавца (settings)");
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Реквизиты продавца в settings повреждены");
        }
    }

    private Map<String, Object> buyerSnapshot(Company company, CreateRequest request) {
        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("name", company.getName());
        if (company.getShortName() != null) buyer.put("shortName", company.getShortName());
        if (company.getUnp() != null) buyer.put("unp", company.getUnp());
        if (company.getLegalAddress() != null) buyer.put("address", company.getLegalAddress());
        // Грузополучатель отличается от покупателя — фиксируем вложенным снапшотом
        if (notBlank(request.getConsigneeName()) || notBlank(request.getConsigneeAddress())) {
            Map<String, Object> consignee = new LinkedHashMap<>();
            consignee.put("name", notBlank(request.getConsigneeName())
                    ? request.getConsigneeName() : company.getName());
            consignee.put("address", notBlank(request.getConsigneeAddress())
                    ? request.getConsigneeAddress() : company.getLegalAddress());
            buyer.put("consignee", consignee);
        }
        return buyer;
    }

    private Map<String, Object> carrierSnapshot(CreateRequest request) {
        Map<String, Object> carrier = new LinkedHashMap<>();
        if (notBlank(request.getVehicle())) carrier.put("vehicle", request.getVehicle());
        if (notBlank(request.getDriver())) carrier.put("driver", request.getDriver());
        if (notBlank(request.getCarrierCompany())) carrier.put("carrierCompany", request.getCarrierCompany());
        return carrier.isEmpty() ? null : carrier;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Серия из settings ('document.series.tn'/'ttn'); seed — "A". */
    private String series(WaybillEntity.Type type) {
        String key = type == WaybillEntity.Type.TTN ? "document.series.ttn" : "document.series.tn";
        String json = settingsService.getString(key);
        String series = "A";
        if (json != null) {
            try {
                String parsed = objectMapper.readTree(json).asText();
                if (parsed != null && !parsed.isBlank()) {
                    series = parsed;
                }
            } catch (Exception ignored) {
                // повреждённое значение settings — используем серию по умолчанию
            }
        }
        // защита имени sequence: только буквы/цифры
        if (!series.matches("[A-Za-zА-Яа-яЁё0-9]{1,10}")) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Недопустимая серия документов: " + series);
        }
        return series.toUpperCase();
    }

    /** ТН-<серия>-<N> / ТТН-<серия>-<N> (2.2): sequence (тип, серия, год); ротация — S31. */
    private String nextNumber(WaybillEntity.Type type, String series) {
        int year = Year.now().getValue();
        String prefix = type == WaybillEntity.Type.TTN ? "ttn" : "tn";
        String cyrillicPrefix = type == WaybillEntity.Type.TTN ? "ТТН" : "ТН";
        String sequence = "doc_seq_" + prefix + "_" + series + "_" + year;
        try {
            Long next = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
            return String.format("%s-%s-%d", cyrillicPrefix, series, next);
        } catch (Exception e) {
            // смена серии/года до ротации планировщиком (S31) — создаём sequence на месте
            jdbcTemplate.execute("create sequence if not exists \"" + sequence + "\"");
            Long next = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
            return String.format("%s-%s-%d", cyrillicPrefix, series, next);
        }
    }

    private WaybillResponse toResponse(WaybillEntity waybill, List<WaybillItem> items) {
        WaybillResponse response = new WaybillResponse();
        response.setId(waybill.getId());
        response.setType(waybill.getType());
        response.setNumber(waybill.getNumber());
        response.setSeries(waybill.getSeries());
        response.setDisplayNumber(waybill.getNumber() + " от " + waybill.getIssueDate().format(RU_DATE));
        response.setIssueDate(waybill.getIssueDate());
        response.setOrderId(waybill.getOrderId());
        response.setInvoiceId(waybill.getInvoiceId());
        response.setSellerSnapshot(waybill.getSellerSnapshot());
        response.setBuyerSnapshot(waybill.getBuyerSnapshot());
        response.setCarrierSnapshot(waybill.getCarrierSnapshot());
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<ItemResponse> itemResponses = new ArrayList<>();
        for (WaybillItem item : items) {
            totalAmount = totalAmount.add(item.getTotal());
            totalVat = totalVat.add(item.getTotal().multiply(item.getVatRate())
                    .divide(BigDecimal.valueOf(100).add(item.getVatRate()), 2, RoundingMode.HALF_UP));
            if (item.getWeight() != null) {
                totalWeight = totalWeight.add(item.getWeight());
            }
            ItemResponse itemResponse = new ItemResponse();
            itemResponse.setSku(brand.text(item.getProductSnapshot(), "sku"));
            itemResponse.setProductName(brand.text(item.getProductSnapshot(), "name"));
            itemResponse.setUnit(brand.text(item.getProductSnapshot(), "unit"));
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setVatRate(item.getVatRate());
            itemResponse.setTotal(item.getTotal());
            itemResponse.setWeight(item.getWeight());
            itemResponses.add(itemResponse);
        }
        response.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        response.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        response.setTotalWeight(totalWeight.setScale(3, RoundingMode.HALF_UP));
        response.setCreatedAt(waybill.getCreatedAt());
        response.setItems(itemResponses);
        return response;
    }
}
