package by.gusto.admin.ops;

import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.common.settings.SettingsService;
import by.gusto.integration.service.XlsxImportService;
import by.gusto.integration.dto.ImportReport;
import by.gusto.auth.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Операционный центр админки (S38): настройки (реквизиты, серии, НДС, склад
 * по умолчанию, гейты), журнал аудита с фильтрами, дашборд процессов,
 * запуск импорта 1С без Bruno. SMTP/Telegram-токены остаются в .env (S33/S32).
 */
@RestController
@RequestMapping("/api/v1/admin/ops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsController {

    /** Ключи настроек, доступные из админки (S38). Транспортные — нет. */
    private static final List<String> EDITABLE_KEYS = List.of(
            "seller.requisites", "document.series.ttn", "document.series.tn",
            "vat.default", "stock.default_location", "auth.require_email_confirmation",
            "landing.hero", "landing.delivery");

    private final SettingsService settingsService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final XlsxImportService importService;
    private final AuthContext authContext;

    // ----- настройки -------------------------------------------------------------

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<SettingItem>>> settings() {
        List<SettingItem> items = new ArrayList<>();
        for (String key : EDITABLE_KEYS) {
            String json = settingsService.getString(key);
            items.add(new SettingItem(key, json));
        }
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateSetting(
            @RequestBody SettingUpdate update) {
        if (!EDITABLE_KEYS.contains(update.getKey())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_FAILED", "Ключ недоступен для правки"));
        }
        try {
            objectMapper.readTree(update.getValue());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_FAILED", "Значение должно быть корректным JSON"));
        }
        jdbcTemplate.update(
                "insert into settings (key, value) values (?::text, ?::jsonb) "
                        + "on conflict (key) do update set value = excluded.value",
                update.getKey(), update.getValue());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Настройка сохранена")));
    }

    // ----- аудит -----------------------------------------------------------------

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        StringBuilder sql = new StringBuilder(
                "select a.created_at, a.action, a.target_type, u.email as actor "
                        + "from audit_log a left join users u on u.id = a.actor_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append("and a.action ilike ? ");
            args.add(action + "%");
        }
        if (targetType != null && !targetType.isBlank()) {
            sql.append("and a.target_type = ? ");
            args.add(targetType);
        }
        if (targetId != null && !targetId.isBlank()) {
            sql.append("and a.target_id = ?::uuid ");
            args.add(targetId);
        }
        sql.append("order by a.created_at desc limit ? offset ?");
        args.add(Math.min(size, 200));
        args.add(page * Math.min(size, 200));
        return ResponseEntity.ok(ApiResponse.success(jdbcTemplate.queryForList(sql.toString(), args.toArray())));
    }

    // ----- дашборд процессов ------------------------------------------------------

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processDashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ordersToday", scalar(
                "select count(*) from orders where created_at >= current_date"));
        result.put("requestsToday", scalar(
                "select count(*) from site_requests where created_at >= current_date"));
        result.put("unassignedOrders", scalar(
                "select count(*) from orders where manager_id is null and status = 'NEW'"));
        // позиции ниже минималки: по товарам с заданным min_stock
        result.put("belowMinStock", scalar(
                "select count(*) from stock_balances b join products p on p.id = b.product_id "
                        + "where p.min_stock is not null and b.quantity < p.min_stock"));
        result.put("unpaidInvoices", scalar(
                "select count(*) from invoices where status in ('ISSUED','PARTIALLY_PAID')"));
        result.put("unpaidAmount", scalar(
                "select coalesce(sum(total_amount), 0) from invoices where status in ('ISSUED','PARTIALLY_PAID')"));
        result.put("overdueTasks", scalar(
                "select count(*) from crm_tasks where status = 'OPEN' and due_date < now()"));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Object scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, Object.class);
    }

    // ----- импорт из админки ------------------------------------------------------

    @PostMapping("/import/prices")
    public ResponseEntity<ApiResponse<ImportReport>> importPrices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean archiveMissing) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(
                importService.importPrices(file, archiveMissing, actor)));
    }

    @PostMapping("/import/stock")
    public ResponseEntity<ApiResponse<ImportReport>> importStock(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                importService.importStock(file, authContext.getCurrentUser())));
    }

    public record SettingItem(String key, String value) {
    }

    @Data
    public static class SettingUpdate {
        private String key;
        private String value;
    }
}
