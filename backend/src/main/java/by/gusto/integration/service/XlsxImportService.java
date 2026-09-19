package by.gusto.integration.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.User;
import by.gusto.catalog.entity.PriceList;
import by.gusto.catalog.entity.Product;
import by.gusto.catalog.entity.ProductPrice;
import by.gusto.catalog.repository.PriceListRepository;
import by.gusto.catalog.repository.ProductPriceRepository;
import by.gusto.catalog.repository.ProductRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.service.FileStorageService;
import by.gusto.integration.dto.ImportReport;
import by.gusto.integration.entity.IntegrationFileEntity;
import by.gusto.integration.repository.IntegrationFileRepository;
import by.gusto.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Импорт .xlsx из 1С (S35): прайсы (SKU → цена) и остатки (SKU → количество).
 * Правила: upsert по SKU; отсутствующие в файле товары → архив (is_active=false,
 * только для прайсов и по явному флагу). Отчёт по строкам (ok/error с номерами),
 * файл и отчёт хранятся в integration_files + files (PRIVATE), результат — в audit_log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XlsxImportService {

    private record ParsedRow(int rowNumber, String sku, BigDecimal value, String error) {
    }

    private final IntegrationFileRepository integrationFileRepository;
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceListRepository priceListRepository;
    private final StockService stockService;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    @Transactional
    public ImportReport importPrices(MultipartFile file, boolean archiveMissing, User actor) {
        List<ParsedRow> rows = parse(file, "PRICES");

        PriceList priceList = priceListRepository.findFirstActiveByDate(LocalDate.now())
                .orElseGet(() -> priceListRepository.save(PriceList.builder()
                        .name("Прайс 1С от " + LocalDate.now())
                        .validFrom(LocalDate.now())
                        .active(true)
                        .build()));
        Map<UUID, ProductPrice> existingPrices = new java.util.LinkedHashMap<>();
        productPriceRepository.findAllByPriceListIdOrderByValidFromDesc(priceList.getId())
                .forEach(p -> existingPrices.putIfAbsent(p.getProductId(), p));

        List<ImportReport.RowError> errors = new ArrayList<>();
        int ok = 0;
        List<UUID> seenProductIds = new ArrayList<>();
        for (ParsedRow parsed : rows) {
            if (parsed.error() != null) {
                errors.add(new ImportReport.RowError(parsed.rowNumber(), parsed.error()));
                continue;
            }
            Optional<Product> product = productRepository.findBySkuAndDeletedAtIsNull(parsed.sku());
            if (product.isEmpty()) {
                errors.add(new ImportReport.RowError(parsed.rowNumber(), "Товар не найден: " + parsed.sku()));
                continue;
            }
            upsertPrice(product.get(), parsed.value(), priceList, existingPrices);
            seenProductIds.add(product.get().getId());
            ok++;
        }

        int archived = 0;
        if (archiveMissing) {
            for (Product product : productRepository.findAllByDeletedAtIsNull()) {
                if (!seenProductIds.contains(product.getId()) && product.isActive()) {
                    product.setActive(false);
                    productRepository.save(product);
                    archived++;
                }
            }
        }

        ImportReport report = finish(file, "PRICES", rows.size(), ok, errors, actor);
        auditService.append(actor.getId(), "IMPORT_PRICES", "integration_file",
                report.integrationFileId(),
                null,
                Map.of("rowsTotal", report.rowsTotal(), "rowsOk", ok,
                        "rowsError", errors.size(), "archived", archived));
        return report;
    }

    @Transactional
    public ImportReport importStock(MultipartFile file, User actor) {
        List<ParsedRow> rows = parse(file, "STOCK");

        UUID locationId = stockService.defaultLocationId();
        List<ImportReport.RowError> errors = new ArrayList<>();
        int ok = 0;
        for (ParsedRow parsed : rows) {
            if (parsed.error() != null) {
                errors.add(new ImportReport.RowError(parsed.rowNumber(), parsed.error()));
                continue;
            }
            Optional<Product> product = productRepository.findBySkuAndDeletedAtIsNull(parsed.sku());
            if (product.isEmpty()) {
                errors.add(new ImportReport.RowError(parsed.rowNumber(), "Товар не найден: " + parsed.sku()));
                continue;
            }
            BigDecimal current = stockService.balanceOf(product.get().getId(), locationId);
            BigDecimal diff = parsed.value().subtract(current);
            if (diff.signum() != 0) {
                stockService.applyMovement(product.get().getId(), locationId,
                        diff.signum() > 0
                                ? by.gusto.inventory.entity.StockMovement.Type.INCOMING
                                : by.gusto.inventory.entity.StockMovement.Type.OUTGOING,
                        diff.abs(), "IMPORT_1C", null,
                        "Импорт остатков из 1С", actor.getId());
            }
            ok++;
        }

        ImportReport report = finish(file, "STOCK", rows.size(), ok, errors, actor);
        auditService.append(actor.getId(), "IMPORT_STOCK", "integration_file",
                report.integrationFileId(),
                null,
                Map.of("rowsTotal", report.rowsTotal(), "rowsOk", ok, "rowsError", errors.size()));
        return report;
    }

    // ----- internals -------------------------------------------------------------

    /** Upsert цены в активном прайс-листе (создаётся, если нет). */
    private void upsertPrice(Product product, BigDecimal price, PriceList priceList,
                             Map<UUID, ProductPrice> existingPrices) {
        ProductPrice existing = existingPrices.get(product.getId());
        if (existing != null) {
            existing.setPrice(price);
            productPriceRepository.save(existing);
        } else {
            ProductPrice created = productPriceRepository.save(ProductPrice.builder()
                    .priceListId(priceList.getId())
                    .productId(product.getId())
                    .price(price)
                    .validFrom(LocalDate.now())
                    .build());
            existingPrices.put(product.getId(), created);
        }
    }

    /** Разбор .xlsx: колонки A=SKU, B=значение; первая строка-заголовок пропускается. */
    private List<ParsedRow> parse(MultipartFile file, String expectedType) {
        if (file == null || file.isEmpty()) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Файл не передан");
        }
        List<ParsedRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(is)) {
            var sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String sku = formatter.formatCellValue(cell(row, 0)).trim();
                String valueText = formatter.formatCellValue(cell(row, 1)).trim().replace(',', '.');
                if (sku.isEmpty() && valueText.isEmpty()) {
                    continue;
                }
                BigDecimal value;
                try {
                    value = new BigDecimal(valueText);
                    if (value.signum() < 0) {
                        throw new NumberFormatException();
                    }
                } catch (Exception e) {
                    rows.add(new ParsedRow(i + 1, sku, null, "Некорректное значение: " + valueText));
                    continue;
                }
                rows.add(new ParsedRow(i + 1, sku, value, null));
            }
        } catch (GustoException e) {
            throw e;
        } catch (Exception e) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Не удалось прочитать .xlsx: " + e.getMessage());
        }
        return rows;
    }

    private Cell cell(Row row, int index) {
        return row.getCell(index, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private ImportReport finish(MultipartFile source, String type, int total, int ok,
                                List<ImportReport.RowError> errors, User actor) {
        UUID fileId = storeXlsx(source, "import-" + type.toLowerCase() + "-" + UUID.randomUUID() + ".xlsx", actor);
        IntegrationFileEntity entity = integrationFileRepository.save(IntegrationFileEntity.builder()
                .direction(IntegrationFileEntity.Direction.IMPORT)
                .type(IntegrationFileEntity.Type.valueOf(type))
                .fileId(fileId)
                .status(IntegrationFileEntity.Status.PROCESSING)
                .processedBy(actor.getId())
                .build());

        UUID errorLogFileId = null;
        if (!errors.isEmpty()) {
            StringBuilder logText = new StringBuilder("row;error\n");
            errors.forEach(e -> logText.append(e.row()).append(";").append(e.message()).append("\n"));
            errorLogFileId = storeBytes("import-errors-" + UUID.randomUUID() + ".csv", actor,
                    logText.toString().getBytes(StandardCharsets.UTF_8));
        }

        // частичные ошибки не делают файл провальным: отчёт с номерами строк — в error_log
        entity.setStatus(IntegrationFileEntity.Status.DONE);
        entity.setRowsTotal(total);
        entity.setRowsOk(ok);
        entity.setRowsError(errors.size());
        entity.setErrorLogFileId(errorLogFileId);
        entity.setProcessedAt(java.time.Instant.now());
        entity = integrationFileRepository.save(entity);

        return new ImportReport(entity.getId(), type, entity.getStatus().name(),
                total, ok, errors.size(), errors);
    }

    private UUID storeXlsx(MultipartFile source, String name, User actor) {
        try {
            return storeBytes(name, actor, source.getBytes());
        } catch (Exception e) {
            throw new GustoException(ErrorCode.INTERNAL, "Не удалось сохранить файл импорта");
        }
    }

    private UUID storeBytes(String name, User actor, byte[] bytes) {
        String storageKey = UUID.randomUUID().toString();
        fileStorageService.store(new ByteArrayInputStream(bytes), storageKey);
        FileEntity file = fileRepository.save(FileEntity.builder()
                .storageKey(storageKey)
                .originalName(name)
                .mimeType(name.endsWith(".csv") ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .sizeBytes((long) bytes.length)
                .ownerId(actor.getId())
                .visibility(FileEntity.Visibility.PRIVATE)
                .build());
        return file.getId();
    }
}
