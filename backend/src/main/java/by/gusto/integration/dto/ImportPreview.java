package by.gusto.integration.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Предпросмотр импорта (S38): строки файла с результатом валидации,
 * без записи в БД. Строки отдаются с обрезкой (первые 100) — полный отчёт
 * ошибок формирует применение импорта (S35).
 */
public record ImportPreview(String type, int rowsTotal, int errorsCount, List<RowPreview> rows) {

    public record RowPreview(int row, String sku, BigDecimal value, boolean ok, String message) {
    }
}
