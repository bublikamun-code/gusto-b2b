package by.gusto.admin.service;

import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.common.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Дашборд процессов (S38): новые заказы/заявки за сегодня, неоплаченные счета
 * (остаток к оплате), позиции ниже min_stock на складе по умолчанию (1.6),
 * просроченные задачи менеджеров (S29). Только чтение — данными управляют
 * свои модули.
 */
@Service
@RequiredArgsConstructor
public class OperationsDashboardService {

    private static final int LIST_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public Dashboard build() {
        UUID locationId = settingsService.getUuid(SettingsService.DEFAULT_LOCATION)
                .orElseThrow(() -> new GustoException(ErrorCode.INTERNAL,
                        "Не задан склад по умолчанию (stock.default_location)"));
        return new Dashboard(
                countToday("orders"),
                countToday("site_requests"),
                unpaidInvoices(),
                lowStock(locationId),
                overdueTasks());
    }

    private long countToday(String table) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where created_at >= current_date",
                Long.class);
        return count == null ? 0 : count;
    }

    private UnpaidInvoices unpaidInvoices() {
        return jdbcTemplate.queryForObject("""
                        select count(*) as cnt,
                               coalesce(sum(i.total_amount - coalesce(p.paid, 0)), 0) as outstanding
                        from invoices i
                        left join (select invoice_id, sum(amount) as paid from payments group by invoice_id) p
                               on p.invoice_id = i.id
                        where i.status in ('ISSUED', 'PARTIALLY_PAID')
                        """,
                (rs, i) -> new UnpaidInvoices(rs.getLong("cnt"), rs.getBigDecimal("outstanding")));
    }

    private List<LowStockItem> lowStock(UUID locationId) {
        return jdbcTemplate.query("""
                        select p.id, p.sku, p.name,
                               (b.quantity - b.reserved) as available, p.min_stock
                        from stock_balances b
                        join products p on p.id = b.product_id
                        where b.location_id = ?
                          and p.deleted_at is null and p.is_active
                          and (b.quantity - b.reserved) < p.min_stock
                        order by (b.quantity - b.reserved) - p.min_stock asc
                        limit ?
                        """,
                (rs, i) -> new LowStockItem(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getString("name"), rs.getBigDecimal("available"),
                        rs.getBigDecimal("min_stock")),
                locationId, LIST_LIMIT);
    }

    private List<OverdueTask> overdueTasks() {
        return jdbcTemplate.query("""
                        select t.id, t.title, t.due_date, u.full_name as assignee
                        from crm_tasks t
                        join users u on u.id = t.assignee_id
                        where t.status = 'OPEN' and t.due_date is not null and t.due_date < now()
                        order by t.due_date asc
                        limit ?
                        """,
                (rs, i) -> new OverdueTask(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("assignee"), rs.getTimestamp("due_date").toInstant()),
                LIST_LIMIT);
    }

    public record Dashboard(long ordersToday, long requestsToday, UnpaidInvoices unpaidInvoices,
                            List<LowStockItem> lowStock, List<OverdueTask> overdueTasks) {
    }

    public record UnpaidInvoices(long count, BigDecimal outstanding) {
    }

    public record LowStockItem(UUID productId, String sku, String productName,
                               BigDecimal available, BigDecimal minStock) {
    }

    public record OverdueTask(UUID id, String title, String assigneeName, Instant dueDate) {
    }
}
