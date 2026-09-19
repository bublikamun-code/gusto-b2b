package by.gusto.integration.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.service.FileStorageService;
import by.gusto.integration.entity.IntegrationFileEntity;
import by.gusto.integration.repository.IntegrationFileRepository;
import by.gusto.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Экспорт .xlsx для 1С (S36): заказы, счета и накладные за период.
 * Колонки — docs/1c-export-format.md. Файл пишется в files (PRIVATE) и
 * фиксируется в integration_files (EXPORT/DONE) + audit_log.
 */
@Service
@RequiredArgsConstructor
public class XlsxExportService {

    private final JdbcTemplate jdbcTemplate;
    private final IntegrationFileRepository integrationFileRepository;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    @Transactional
    public byte[] exportOrders(LocalDate from, LocalDate to, User actor) {
        String sql = """
                select o.number, o.created_at, o.status, o.delivery_type,
                       coalesce(c.name, 'Розница') as client, o.total_amount, o.total_vat
                from orders o left join companies c on c.id = o.customer_company_id
                where o.created_at >= ?::date and o.created_at < ?::date + interval '1 day'
                order by o.created_at
                """;
        String[] headers = {"Номер", "Дата", "Статус", "Доставка", "Клиент", "Сумма, BYN", "НДС, BYN"};
        byte[] bytes = buildWorkbook(headers, jdbcTemplate.queryForList(sql,
                java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)), row -> new Object[]{
                row.get("number"), row.get("created_at"), row.get("status"), row.get("delivery_type"),
                row.get("client"), row.get("total_amount"), row.get("total_vat")});
        register("ORDERS", from, to, bytes, actor);
        return bytes;
    }

    @Transactional
    public byte[] exportInvoices(LocalDate from, LocalDate to, User actor) {
        String sql = """
                select i.number, i.issue_date, i.status, c.name as client, c.unp,
                       i.total_amount, i.total_vat
                from invoices i left join companies c on c.id = i.customer_company_id
                where i.issue_date >= ? and i.issue_date <= ?
                order by i.issue_date
                """;
        String[] headers = {"Счёт", "Дата", "Статус", "Покупатель", "УНП", "Сумма, BYN", "НДС, BYN"};
        byte[] bytes = buildWorkbook(headers, jdbcTemplate.queryForList(sql,
                java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)), row -> new Object[]{
                row.get("number"), row.get("issue_date"), row.get("status"), row.get("client"),
                row.get("unp"), row.get("total_amount"), row.get("total_vat")});
        register("INVOICES", from, to, bytes, actor);
        return bytes;
    }

    @Transactional
    public byte[] exportWaybills(LocalDate from, LocalDate to, User actor) {
        String sql = """
                select w.number, w.type, w.issue_date, c.name as client, w.total_amount
                from waybills w left join orders o on o.id = w.order_id
                left join companies c on c.id = o.customer_company_id
                where w.issue_date >= ? and w.issue_date <= ?
                order by w.issue_date
                """;
        String[] headers = {"Накладная", "Тип", "Дата", "Покупатель", "Сумма, BYN"};
        byte[] bytes = buildWorkbook(headers, jdbcTemplate.queryForList(sql,
                java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)), row -> new Object[]{
                row.get("number"), row.get("type"), row.get("issue_date"), row.get("client"),
                row.get("total_amount")});
        register("WAYBILLS", from, to, bytes, actor);
        return bytes;
    }

    /** Прайс клиента со своими ценами (S36; юрлицо — выгрузка для 1С). */
    @Transactional
    public byte[] exportClientPricing(User client) {
        if (client.getCompanyId() == null) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "К пользователю не привязана компания");
        }
        String sql = """
                select p.sku, p.name, p.unit, p.weight_per_unit,
                       coalesce(cp.price, pp.price) as price
                from products p
                left join lateral (
                    select price from product_prices pp
                    where pp.product_id = p.id and pp.valid_from <= now()::date
                      and (pp.valid_to is null or pp.valid_to >= now()::date)
                    order by pp.valid_from desc limit 1
                ) pp on true
                left join lateral (
                    select price from customer_prices cp
                    where cp.product_id = p.id and cp.company_id = ?
                ) cp on true
                where p.deleted_at is null and p.is_active
                order by p.sku
                """;
        String[] headers = {"Артикул", "Наименование", "Ед.", "Вес ед., кг", "Цена, BYN"};
        byte[] bytes = buildWorkbook(headers, jdbcTemplate.queryForList(sql, client.getCompanyId()),
                row -> new Object[]{
                        row.get("sku"), row.get("name"), row.get("unit"),
                        row.get("weight_per_unit"), row.get("price")});
        registerClient("PRICES", bytes, client);
        return bytes;
    }

    // ----- internals -------------------------------------------------------------

    private interface RowMapper {
        Object[] map(Map<String, Object> row);
    }

    private byte[] buildWorkbook(String[] headers, List<Map<String, Object>> rows, RowMapper mapper) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("export");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 16 * 256);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row dataRow = sheet.createRow(r++);
                Object[] values = mapper.map(row);
                for (int c = 0; c < values.length; c++) {
                    Cell cell = dataRow.createCell(c);
                    Object value = values[c];
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось сформировать .xlsx: " + e.getMessage());
        }
    }

    private void register(String type, LocalDate from, LocalDate to, byte[] bytes, User actor) {
        UUID fileId = storeBytes("export-" + type.toLowerCase() + "-" + UUID.randomUUID() + ".xlsx",
                bytes, actor);
        integrationFileRepository.save(IntegrationFileEntity.builder()
                .direction(IntegrationFileEntity.Direction.EXPORT)
                .type(IntegrationFileEntity.Type.valueOf(type))
                .fileId(fileId)
                .status(IntegrationFileEntity.Status.DONE)
                .rowsTotal(countRows(type, from, to))
                .processedBy(actor.getId())
                .processedAt(java.time.Instant.now())
                .build());
        auditService.append(actor.getId(), "EXPORT_" + type, "integration_file", null,
                null, Map.of("from", from.toString(), "to", to.toString()));
    }

    private void registerClient(String type, byte[] bytes, User actor) {
        UUID fileId = storeBytes("pricing-" + UUID.randomUUID() + ".xlsx", bytes, actor);
        integrationFileRepository.save(IntegrationFileEntity.builder()
                .direction(IntegrationFileEntity.Direction.EXPORT)
                .type(IntegrationFileEntity.Type.valueOf(type))
                .fileId(fileId)
                .status(IntegrationFileEntity.Status.DONE)
                .processedBy(actor.getId())
                .processedAt(java.time.Instant.now())
                .build());
    }

    private int countRows(String type, LocalDate from, LocalDate to) {
        String table = switch (type) {
            case "ORDERS" -> "orders";
            case "INVOICES" -> "invoices";
            case "WAYBILLS" -> "waybills";
            default -> null;
        };
        if (table == null) {
            return 0;
        }
        String dateColumn = table.equals("orders") ? "created_at" : "issue_date";
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + dateColumn + " >= ?::date "
                        + "and " + dateColumn + " < ?::date + interval '1 day'",
                Integer.class, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        return count == null ? 0 : count;
    }

    private UUID storeBytes(String name, byte[] bytes, User actor) {
        String storageKey = UUID.randomUUID().toString();
        fileStorageService.store(new ByteArrayInputStream(bytes), storageKey);
        FileEntity file = fileRepository.save(FileEntity.builder()
                .storageKey(storageKey)
                .originalName(name)
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .sizeBytes((long) bytes.length)
                .ownerId(actor.getId())
                .visibility(FileEntity.Visibility.PRIVATE)
                .build());
        return file.getId();
    }
}
