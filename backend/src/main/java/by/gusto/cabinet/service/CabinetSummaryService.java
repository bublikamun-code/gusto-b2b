package by.gusto.cabinet.service;

import by.gusto.cabinet.dto.CabinetSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/**
 * Сводка кабинета клиента для дашборда.
 *
 * <p>Все суммы считаются строго по {@code companyId} из токена — переданного
 * параметра нет вовсе, поэтому подставить чужую компанию нечем.
 *
 * <p>Розница (физлицо, {@code companyId == null}) тоже получает сводку, но без
 * долга и счетов: их у физлица нет (матрица 2.1).
 */
@Service
@RequiredArgsConstructor
public class CabinetSummaryService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public CabinetSummaryResponse summary(UUID companyId, UUID userId, String companyName) {
        if (companyId == null) {
            return retailSummary(userId, companyName);
        }

        Map<String, Object> orders = firstRow(jdbcTemplate.queryForList("""
                select count(*)::int as active_count,
                       coalesce(sum(total_amount), 0) as active_total,
                       count(*) filter (where status = 'NEW')::int as awaiting_count
                from orders
                where customer_company_id = ?
                  and status in ('NEW','CONFIRMED','PROCESSING','READY','SHIPPED')
                """, companyId));

        Map<String, Object> invoices = firstRow(jdbcTemplate.queryForList("""
                select count(*)::int as unpaid_count,
                       coalesce(sum(total_amount), 0) - coalesce((select sum(p.amount)
                            from payments p
                            join invoices i2 on p.invoice_id = i2.id
                            where i2.customer_company_id = ?
                              and i2.status in ('ISSUED','PARTIALLY_PAID')), 0) as debt
                from invoices
                where customer_company_id = ?
                  and status in ('ISSUED','PARTIALLY_PAID')
                """, companyId, companyId));

        Map<String, Object> lastOrder = firstRow(jdbcTemplate.queryForList("""
                select number, status, created_at
                from orders
                where customer_company_id = ?
                order by created_at desc
                limit 1
                """, companyId));

        Timestamp lastAt = lastOrder.get("created_at") instanceof Timestamp ts ? ts : null;

        return CabinetSummaryResponse.builder()
                .companyName(companyName)
                .activeOrdersCount(toLong(orders.get("active_count")))
                .activeOrdersTotal(toBigDecimal(orders.get("active_total")))
                .awaitingConfirmationCount(toLong(orders.get("awaiting_count")))
                .unpaidInvoicesCount(toLong(invoices.get("unpaid_count")))
                .outstandingDebt(toBigDecimal(invoices.get("debt")))
                .lastOrderNumber((String) lastOrder.get("number"))
                .lastOrderStatus((String) lastOrder.get("status"))
                .lastOrderAt(lastAt == null ? null : lastAt.toInstant())
                .build();
    }

    /** Розница: заказы видны, расчёты — нет. */
    private CabinetSummaryResponse retailSummary(UUID userId, String companyName) {
        Map<String, Object> orders = firstRow(jdbcTemplate.queryForList("""
                select count(*)::int as active_count,
                       coalesce(sum(total_amount), 0) as active_total,
                       count(*) filter (where status = 'NEW')::int as awaiting_count
                from orders
                where customer_company_id is null
                  and customer_user_id = ?
                  and status in ('NEW','CONFIRMED','PROCESSING','READY','SHIPPED')
                """, userId));
        return CabinetSummaryResponse.builder()
                .companyName(companyName)
                .activeOrdersCount(toLong(orders.get("active_count")))
                .activeOrdersTotal(toBigDecimal(orders.get("active_total")))
                .awaitingConfirmationCount(toLong(orders.get("awaiting_count")))
                .unpaidInvoicesCount(0)
                .outstandingDebt(BigDecimal.ZERO)
                .build();
    }

    /**
     * Первая строка результата либо пустая карта.
     *
     * <p>queryForMap на пустом результате бросает EmptyResultDataAccessException, а
     * пустой результат здесь — норма: у нового клиента нет ни заказов, ни счетов.
     * Наивная версия отдавала 500 именно тем клиентам, которым кабинет нужнее всего
     * (проверено на стенде: у только что заведённой компании дашборд был 500).
     */
    private Map<String, Object> firstRow(java.util.List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal
                ? decimal.setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
    }
}