import type { ImportPreview, ImportReport } from "../api/adminOperations";

/**
 * Мастер импорта 1С (S38): краткие формулировки состояния мастера.
 */

/** Готов ли файл к применению после предпросмотра. */
export function previewIsClean(preview: ImportPreview): boolean {
  return preview.errorsCount === 0;
}

/** Что скажем пользователю после применения импорта. */
export function importResultMessage(report: ImportReport): string {
  if (report.rowsError === 0) {
    return `Импорт выполнен: все ${report.rowsOk} строк без ошибок`;
  }
  return `Импорт выполнен: OK ${report.rowsOk}, ошибок ${report.rowsError}`;
}
