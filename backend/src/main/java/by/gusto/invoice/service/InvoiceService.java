package by.gusto.invoice.service;

import by.gusto.common.api.Pages;
import by.gusto.audit.AuditService;
import by.gusto.auth.entity.Role;
import by.gusto.auth.service.AuthorizationService;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.common.settings.SettingsService;
import by.gusto.company.entity.Company;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.invoice.dto.InvoiceDtos;
import by.gusto.invoice.dto.InvoiceDtos.InvoiceResponse;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.entity.InvoiceEntity.Status;
import by.gusto.invoice.entity.InvoiceItem;
import by.gusto.invoice.repository.InvoiceItemRepository;
import by.gusto.invoice.repository.InvoiceRepository;
import by.gusto.order.entity.OrderEntity;
import by.gusto.order.entity.OrderItem;
import by.gusto.order.repository.OrderItemRepository;
import by.gusto.order.repository.OrderRepository;
import by.gusto.outbox.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * Счета (S24): создание из заказа снапшотами продавца/покупателя/товаров,
 * нумерация СЧ-N по sequence (2.2), НДС выделяется расчётно (2.3).
 * Снапшоты после выпуска не меняются — смена реквизитов не переписывает счета.
 * PARTIALLY_PAID/PAID — платежами в S28.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authz;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final InvoicePdfService invoicePdfService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public InvoiceResponse createFromOrder(UUID orderId, User actor) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Заказ не найден"));

        // Мутации счёта требовали заказа, но не требовали принадлежности компании
        // актору: менеджер выписывал счёт по чужому заказу (S44).
        authz.requireOrderAccess(order.getCustomerCompanyId(), order.getCustomerUserId(), order.getManagerId(), actor);

        if (order.getStatus() == OrderEntity.Status.CANCELLED) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Заказ отменён — счёт не выставляется");
        }
        if (order.getCustomerCompanyId() == null) {
            // розничным заказам счета не выставляются (матрица 2.1)
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Счёт выставляется только заказу юрлица");
        }
        invoiceRepository.findByOrderId(orderId)
                .filter(invoice -> invoice.getStatus() != Status.CANCELLED)
                .ifPresent(invoice -> {
                    throw new GustoException(ErrorCode.INVOICE_ALREADY_EXISTS,
                            "По заказу уже есть счёт " + invoice.getNumber());
                });

        Company company = companyRepository.findById(order.getCustomerCompanyId())
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Компания клиента не найдена"));

        Map<String, Object> seller = sellerSnapshot();
        Map<String, Object> buyer = buyerSnapshot(company);
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Заказ без позиций");
        }

        InvoiceEntity invoice = InvoiceEntity.builder()
                .orderId(orderId)
                .customerCompanyId(company.getId())
                .sellerSnapshot(seller)
                .buyerSnapshot(buyer)
                .issueDate(LocalDate.now())
                .createdBy(actor.getId())
                .totalAmount(BigDecimal.ZERO)
                .totalVat(BigDecimal.ZERO)
                .build();
        invoice.setNumber(nextNumber());
        invoice = invoiceRepository.save(invoice);

        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItems) {
            BigDecimal total = orderItem.getTotal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = vatIncluded(orderItem.getVatRate(), total);
            totalAmount = totalAmount.add(total);
            totalVat = totalVat.add(vat);
            items.add(InvoiceItem.builder()
                    .invoiceId(invoice.getId())
                    .productSnapshot(new LinkedHashMap<>(orderItem.getProductSnapshot()))
                    .quantity(orderItem.getQuantity())
                    .unitPrice(orderItem.getUnitPrice())
                    .vatRate(orderItem.getVatRate())
                    .total(total)
                    .build());
        }
        invoiceItemRepository.saveAll(items);

        invoice.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        invoice = invoiceRepository.save(invoice);

        auditService.append(actor.getId(), "INVOICE_CREATE", "invoice", invoice.getId(),
                null,
                Map.of("number", invoice.getNumber(), "orderId", orderId.toString()));

        return toResponse(invoice, items);
    }

    /** Выпуск счёта: DRAFT → ISSUED. Пишется в audit_log и outbox (2.6). */
    @Transactional
    public InvoiceResponse issue(UUID invoiceId, User actor) {
        InvoiceEntity invoice = loadInvoice(invoiceId);
        requireCanView(invoice, actor);
        if (invoice.getStatus() != Status.DRAFT) {
            throw new GustoException(ErrorCode.INVOICE_INVALID_STATE,
                    "Выпустить можно только черновик, текущий статус: " + invoice.getStatus());
        }
        invoice.setStatus(Status.ISSUED);
        invoice = invoiceRepository.save(invoice);

        // PDF формируется при выпуске (S25): снапшоты уже зафиксированы
        invoicePdfService.ensurePdf(invoice.getId(), actor.getId());

        auditService.append(actor.getId(), "INVOICE_ISSUE", "invoice", invoice.getId(),
                Map.of("status", Status.DRAFT.name()),
                Map.of("status", Status.ISSUED.name(), "number", invoice.getNumber()));

        outboxService.append("invoice", invoice.getId(), "INVOICE_ISSUED", Map.of(
                "invoiceId", invoice.getId().toString(),
                "number", invoice.getNumber(),
                "companyId", invoice.getCustomerCompanyId() == null
                        ? "" : invoice.getCustomerCompanyId().toString(),
                "totalAmount", invoice.getTotalAmount().toPlainString()));

        return toResponse(invoice, invoiceItemRepository.findAllByInvoiceId(invoiceId));
    }

    @Transactional
    public InvoiceResponse cancel(UUID invoiceId, User actor) {
        InvoiceEntity invoice = loadInvoice(invoiceId);
        requireCanView(invoice, actor);
        if (invoice.getStatus() != Status.DRAFT && invoice.getStatus() != Status.ISSUED) {
            throw new GustoException(ErrorCode.INVOICE_INVALID_STATE,
                    "Отменить можно черновик или выпущенный счёт, текущий статус: " + invoice.getStatus());
        }
        // счёт с платежами не отменяется (S28): сначала сторнировать оплаты
        Integer payments = jdbcTemplate.queryForObject(
                "select count(*) from payments where invoice_id = ?", Integer.class, invoiceId);
        if (payments != null && payments > 0) {
            throw new GustoException(ErrorCode.INVOICE_INVALID_STATE,
                    "Счёт имеет зарегистрированные платежи и не может быть отменён");
        }
        // PARTIALLY_PAID/PAID не достижимы до S28; тут — консервативный запрет на отмену оплаченного
        Status before = invoice.getStatus();
        invoice.setStatus(Status.CANCELLED);
        invoice = invoiceRepository.save(invoice);

        auditService.append(actor.getId(), "INVOICE_CANCEL", "invoice", invoice.getId(),
                Map.of("status", before.name()),
                Map.of("status", Status.CANCELLED.name(), "number", invoice.getNumber()));

        return toResponse(invoice, invoiceItemRepository.findAllByInvoiceId(invoiceId));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID invoiceId, User actor) {
        InvoiceEntity invoice = loadInvoice(invoiceId);
        requireCanView(invoice, actor);
        return toResponse(invoice, invoiceItemRepository.findAllByInvoiceId(invoiceId));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(User actor, int page, int size) {
        PageRequest pageable = Pages.of(page, size);
        Page<InvoiceEntity> invoices;
        if (actor.getRole() == Role.MANAGER) {
            invoices = invoiceRepository.findAllVisibleTo(actor.getId(), pageable);
        } else {
            invoices = invoiceRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return invoices.map(invoice -> toResponse(invoice,
                invoiceItemRepository.findAllByInvoiceId(invoice.getId())));
    }

    /** Кабинет юрлица: только выданные счета своей компании (2.1); черновики скрыты. */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listForCompany(UUID companyId, int page, int size) {
        PageRequest pageable = Pages.of(page, size);
        return invoiceRepository
                .findAllByCustomerCompanyIdAndStatusNotOrderByCreatedAtDesc(companyId, Status.DRAFT, pageable)
                .map(invoice -> toResponse(invoice,
                        invoiceItemRepository.findAllByInvoiceId(invoice.getId())));
    }

    // ----- internals -------------------------------------------------------------

    private InvoiceEntity loadInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Счёт не найден"));
    }

    private void requireCanView(InvoiceEntity invoice, User actor) {
        // Раньше для staff проверка была безусловной — менеджер читал счёт любой
        // компании по id; теперь та же матрица, что и в списках (S44).
        authz.requireCompanyAccess(invoice.getCustomerCompanyId(), actor);

        // Черновик клиенту не показываем: это незавершённая работа бухгалтера. Раньше
        // клиент получал счёт и по прямой ссылке на id, и PDF (ensurePdf сгенерировал
        // его на лету, а отличать черновик от выданного в самом PDF нечем)
        // — аудит 2026-09-30, группа «Цифры»/документы.
        if (invoice.getStatus() == Status.DRAFT
                && (actor.getRole() == Role.CUSTOMER_LEGAL
                    || actor.getRole() == Role.CUSTOMER_INDIVIDUAL)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Счёт не найден");
        }
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

    private Map<String, Object> buyerSnapshot(Company company) {
        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("name", company.getName());
        if (company.getShortName() != null) buyer.put("shortName", company.getShortName());
        if (company.getUnp() != null) buyer.put("unp", company.getUnp());
        if (company.getLegalAddress() != null) buyer.put("address", company.getLegalAddress());
        // Ключи — как у продавца (seller.requisites, snake_case): рендерер читает именно
        // bank_account/bank_name/bank_bic. Раньше здесь были camelCase-ключи, множества ключей
        // не пересекались, и банковские реквизиты покупателя не попадали в PDF счёта вообще
        // (аудит 2026-09-30, P1-4).
        if (company.getBankAccount() != null) buyer.put("bank_account", company.getBankAccount());
        if (company.getBankName() != null) buyer.put("bank_name", company.getBankName());
        if (company.getBankBic() != null) buyer.put("bank_bic", company.getBankBic());
        return buyer;
    }

    /** СЧ-<N> (2.2): sequence на год, max(number)+1 запрещён; ротация — S31. */
    private String nextNumber() {
        int year = Year.now().getValue();
        // Как и в накладных (аудит 2026-09-30, P1): сначала создаём sequence, затем nextval.
        // Раньше fallback'а не было вовсе — смена года/сбой планировщика в 00:05 давали 500
        // на оформлении счёта.
        String sequence = "doc_seq_invoice_" + year;
        jdbcTemplate.execute("create sequence if not exists \"" + sequence + "\"");
        long next = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
        return String.format("СЧ-%d", next);
    }

    /** Сумма зарегистрированных оплат по счёту. Отменённые платежи не учитываются. */
    private BigDecimal paidAmountOf(UUID invoiceId) {
        BigDecimal paid = jdbcTemplate.queryForObject(
                "select coalesce(sum(amount), 0) from payments where invoice_id = ?", BigDecimal.class, invoiceId);
        return paid == null ? BigDecimal.ZERO.setScale(2) : paid.setScale(2, RoundingMode.HALF_UP);
    }

    /** Остаток к оплате; не уходит в минус при переплате. */
    private BigDecimal balanceDue(InvoiceEntity invoice) {
        BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        BigDecimal due = total.subtract(paidAmountOf(invoice.getId()));
        return due.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceResponse toResponse(InvoiceEntity invoice, List<InvoiceItem> items) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setDisplayNumber(invoice.getNumber() + " от " + invoice.getIssueDate().format(RU_DATE));
        response.setNumber(invoice.getNumber());
        response.setIssueDate(invoice.getIssueDate());
        response.setOrderId(invoice.getOrderId());
        response.setCustomerCompanyId(invoice.getCustomerCompanyId());
        response.setSellerSnapshot(invoice.getSellerSnapshot());
        response.setBuyerSnapshot(invoice.getBuyerSnapshot());
        response.setTotalAmount(invoice.getTotalAmount());
        response.setTotalVat(invoice.getTotalVat());
        response.setPaidAmount(paidAmountOf(invoice.getId()));
        response.setBalanceDue(balanceDue(invoice));
        response.setStatus(invoice.getStatus());
        response.setCreatedAt(invoice.getCreatedAt());
        response.setItems(items.stream().map(item -> {
            InvoiceDtos.ItemResponse itemResponse = new InvoiceDtos.ItemResponse();
            itemResponse.setSku((String) item.getProductSnapshot().get("sku"));
            itemResponse.setProductName((String) item.getProductSnapshot().get("name"));
            itemResponse.setUnit((String) item.getProductSnapshot().get("unit"));
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setVatRate(item.getVatRate());
            itemResponse.setVat(vatIncluded(item.getVatRate(), item.getTotal()));
            itemResponse.setTotal(item.getTotal());
            return itemResponse;
        }).toList());
        return response;
    }

    /** НДС-включённая цена (2.3): vat = total × rate / (100 + rate). */
    private static BigDecimal vatIncluded(BigDecimal vatRate, BigDecimal total) {
        return total.multiply(vatRate)
                .divide(BigDecimal.valueOf(100).add(vatRate), 2, RoundingMode.HALF_UP);
    }
}
