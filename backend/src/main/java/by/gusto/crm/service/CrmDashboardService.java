package by.gusto.crm.service;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Дашборд руководителя (S29): выручка по выполненным заказам, топ-товары,
 * топ-клиенты, долг (из S28), конверсия лидов. ADMIN — вся база;
 * MANAGER — свои заказы и компании (матрица 2.1 «свои клиенты»).
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class CrmDashboardService {

    private static final String MANAGER_ORDER_FILTER =
            " and (o.manager_id = ? or o.customer_company_id in "
                    + "(select id from companies where manager_id = ?)) ";

    private final JdbcTemplate jdbcTemplate;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(User actor, LocalDate from, LocalDate to) {
        boolean manager = actor.getRole() == Role.MANAGER;
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);

        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(fromDate));
        args.add(java.sql.Date.valueOf(toDate));
        String managerFilter = "";
        if (manager) {
            managerFilter = MANAGER_ORDER_FILTER;
            args.add(actor.getId());
            args.add(actor.getId());
        }

        BigDecimal revenue = jdbcTemplate.queryForObject(
                "select coalesce(sum(o.total_amount), 0) from orders o "
                        + "where o.status = 'COMPLETED' and o.created_at >= cast(? as date) and o.created_at < cast(? as date) + interval '1 day'"
                        + managerFilter,
                BigDecimal.class, args.toArray());

        Long completedOrders = jdbcTemplate.queryForObject(
                "select count(*) from orders o "
                        + "where o.status = 'COMPLETED' and o.created_at >= cast(? as date) and o.created_at < cast(? as date) + interval '1 day'"
                        + managerFilter,
                Long.class, args.toArray());

        List<Map<String, Object>> topProducts = jdbcTemplate.queryForList(
                "select i.product_snapshot->>'name' as name, "
                        + "sum(i.quantity) as quantity, sum(i.total) as total "
                        + "from order_items i join orders o on i.order_id = o.id "
                        + "where o.status = 'COMPLETED' and o.created_at >= cast(? as date) and o.created_at < cast(? as date) + interval '1 day'"
                        + managerFilter.replaceAll("o\\.", "o.")
                        + "group by 1 order by 3 desc limit 5",
                args.toArray());

        String customerManagerFilter = manager
                ? " and (o.manager_id = ? or o.customer_company_id in "
                  + "(select id from companies where manager_id = ?)) "
                : "";
        List<Object> customerArgs = new ArrayList<>(args.subList(0, 2));
        List<Map<String, Object>> topCustomers = jdbcTemplate.queryForList(
                "select c.name as name, count(o.id) as orders, coalesce(sum(o.total_amount), 0) as total "
                        + "from companies c join orders o on o.customer_company_id = c.id "
                        + "where o.status = 'COMPLETED' and o.created_at >= cast(? as date) and o.created_at < cast(? as date) + interval '1 day'"
                        + customerManagerFilter
                        + "group by c.id, c.name order by 3 desc limit 5",
                customerArgs.toArray());

        // долг: только ADMIN видит всю базу; менеджер — сводка по активным счетам его компаний
        BigDecimal debt = BigDecimal.ZERO;
        if (!manager) {
            debt = paymentService.debtReport().stream()
                    .map(row -> row.debt())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            debt = jdbcTemplate.queryForObject(
                    "select coalesce(sum(i.total_amount), 0) - coalesce(("
                            + "select sum(p.amount) from payments p join invoices i2 on p.invoice_id = i2.id "
                            + "where i2.customer_company_id = i.customer_company_id "
                            + "and i2.status in ('ISSUED','PARTIALLY_PAID')), 0) "
                            + "from invoices i join companies c on i.customer_company_id = c.id "
                            + "where i.status in ('ISSUED','PARTIALLY_PAID') "
                            + "and c.manager_id = ?",
                    BigDecimal.class, actor.getId());
            if (debt == null) debt = BigDecimal.ZERO;
            debt = debt.setScale(2, RoundingMode.HALF_UP);
        }

        long leadsWon = jdbcTemplate.queryForObject(
                "select count(*) from leads where status = 'WON'", Long.class);
        long leadsLost = jdbcTemplate.queryForObject(
                "select count(*) from leads where status = 'LOST'", Long.class);
        long leadsTotal = jdbcTemplate.queryForObject("select count(*) from leads", Long.class);
        long closed = leadsWon + leadsLost;
        double conversion = closed == 0 ? 0
                : BigDecimal.valueOf(leadsWon * 100.0 / closed)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", fromDate);
        result.put("to", toDate);
        result.put("revenue", revenue.setScale(2, RoundingMode.HALF_UP));
        result.put("completedOrders", completedOrders);
        result.put("topProducts", topProducts);
        result.put("topCustomers", topCustomers);
        result.put("debt", debt);
        result.put("leadsTotal", leadsTotal);
        result.put("leadsWon", leadsWon);
        result.put("leadConversionPercent", conversion);
        return result;
    }
}
