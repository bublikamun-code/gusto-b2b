package by.gusto.payment.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.invoice.entity.InvoiceEntity;
import by.gusto.invoice.repository.InvoiceRepository;
import by.gusto.payment.dto.PaymentDtos.DebtRow;
import by.gusto.payment.dto.PaymentDtos.PaymentRequest;
import by.gusto.payment.dto.PaymentDtos.PaymentResponse;
import by.gusto.payment.entity.PaymentEntity;
import by.gusto.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Платежи и задолженность (S28): регистрация оплат по выпущенным счетам,
 * частичная оплата → PARTIALLY_PAID, полная → PAID. Отмена счёта с платежами
 * запрещена (см. InvoiceService.cancel). Отчёт долга — активные счета по компаниям.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Активные для долга статусы счёта (PAID/CANCELLED в долг не попадают). */
    private static final String DEBT_STATUSES = "('ISSUED','PARTIALLY_PAID')";

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public PaymentResponse register(UUID invoiceId, PaymentRequest request, User actor) {
        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Счёт не найден"));

        if (invoice.getStatus() != InvoiceEntity.Status.ISSUED
                && invoice.getStatus() != InvoiceEntity.Status.PARTIALLY_PAID) {
            throw new GustoException(ErrorCode.INVOICE_INVALID_STATE,
                    "Оплата принимается только по выпущенному счёту, текущий статус: " + invoice.getStatus());
        }

        BigDecimal paid = paymentRepository.sumByInvoiceId(invoiceId);
        BigDecimal remaining = invoice.getTotalAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Сумма платежа должна быть положительной");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Платёж " + amount + " превышает остаток долга " + remaining);
        }

        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .invoiceId(invoiceId)
                .amount(amount)
                .paidAt(request.getPaidAt() != null ? request.getPaidAt() : LocalDate.now())
                .method(request.getMethod())
                .note(request.getNote())
                .createdBy(actor.getId())
                .build());

        BigDecimal totalPaid = paid.add(amount);
        InvoiceEntity.Status newStatus = totalPaid.compareTo(invoice.getTotalAmount()) >= 0
                ? InvoiceEntity.Status.PAID
                : InvoiceEntity.Status.PARTIALLY_PAID;
        InvoiceEntity.Status oldStatus = invoice.getStatus();
        invoice.setStatus(newStatus);
        invoiceRepository.save(invoice);

        auditService.append(actor.getId(), "PAYMENT_REGISTER", "invoice", invoiceId,
                Map.of("status", oldStatus.name(), "paid", paid.toPlainString()),
                Map.of("status", newStatus.name(),
                        "amount", amount.toPlainString(),
                        "paid", totalPaid.toPlainString()));

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByInvoice(UUID invoiceId) {
        invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Счёт не найден"));
        return paymentRepository.findAllByInvoiceIdOrderByPaidAtAsc(invoiceId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Отчёт «задолженность по клиентам» (S28): по компаниям с активными счетами —
     * выставлено (ISSUED+PARTIALLY_PAID), оплачено по ним, долг > 0.
     */
    @Transactional(readOnly = true)
    public List<DebtRow> debtReport() {
        String sql = """
                select c.id,
                       c.name,
                       coalesce((select sum(i.total_amount) from invoices i
                                 where i.customer_company_id = c.id and i.status in %s), 0),
                       coalesce((select sum(p.amount) from payments p
                                 join invoices i on p.invoice_id = i.id
                                 where i.customer_company_id = c.id and i.status in %s), 0),
                       (select max(i.created_at) from invoices i
                        where i.customer_company_id = c.id and i.status in %s)
                from companies c
                where exists (select 1 from invoices i
                              where i.customer_company_id = c.id and i.status in %s)
                group by c.id, c.name
                order by c.name
                """.formatted(DEBT_STATUSES, DEBT_STATUSES, DEBT_STATUSES, DEBT_STATUSES);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID companyId = UUID.fromString(rs.getString(1));
            String companyName = rs.getString(2);
            BigDecimal invoiced = rs.getBigDecimal(3);
            BigDecimal paid = rs.getBigDecimal(4);
            Timestamp oldest = rs.getTimestamp(5);
            BigDecimal debt = invoiced.subtract(paid).setScale(2, RoundingMode.HALF_UP);
            return new DebtRow(companyId, companyName,
                    invoiced.setScale(2, RoundingMode.HALF_UP),
                    paid.setScale(2, RoundingMode.HALF_UP),
                    debt,
                    oldest != null ? oldest.toInstant() : null);
        });
    }

    private PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(payment.getId(), payment.getInvoiceId(), payment.getAmount(),
                payment.getPaidAt(), payment.getMethod(), payment.getNote(), payment.getCreatedAt());
    }
}
