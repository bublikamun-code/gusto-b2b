import { useRef, useState } from "react";
import { Badge, Button, Card, Input, Table, useToast } from "../../components/ui";
import {
  applyImport,
  downloadExport,
  previewImport,
  type ImportPreview,
  type ImportReport,
} from "../../api/adminOperations";
import { importResultMessage, previewIsClean } from "../../lib/importWizard";
import styles from "./AdminPages.module.scss";
import wizardStyles from "./AdminIntegrationPage.module.scss";

type ImportKind = "prices" | "stock";

const KIND_LABELS: Record<ImportKind, string> = {
  prices: "Прайсы (.xlsx: SKU, цена)",
  stock: "Остатки (.xlsx: SKU, количество)",
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

/** Мастер импорта/экспорта 1С (S38 поверх эндпоинтов S35/S36):
 * загрузка файла → предпросмотр строк → отчёт ошибок → применение. */
export default function AdminIntegrationPage() {
  const { push } = useToast();
  const fileInput = useRef<HTMLInputElement>(null);

  const [kind, setKind] = useState<ImportKind>("prices");
  const [archiveMissing, setArchiveMissing] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [busy, setBusy] = useState(false);

  const [exportKind, setExportKind] = useState<"orders" | "invoices" | "waybills">("orders");
  const [exportFrom, setExportFrom] = useState(daysAgo(30));
  const [exportTo, setExportTo] = useState(today());

  const resetFile = () => {
    setFile(null);
    setPreview(null);
    if (fileInput.current) fileInput.current.value = "";
  };

  const pickFile = (picked: File | null) => {
    setFile(picked);
    setPreview(null);
    setReport(null);
  };

  const runPreview = async () => {
    if (!file) return;
    setBusy(true);
    try {
      setPreview(await previewImport(kind, file));
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const runApply = async () => {
    if (!file) return;
    setBusy(true);
    try {
      const result = await applyImport(kind, file, { archiveMissing });
      setReport(result);
      setPreview(null);
      resetFile();
      push(importResultMessage(result), result.rowsError > 0 ? "info" : "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const runExport = async () => {
    if (exportFrom > exportTo) {
      push("Дата начала позже даты окончания", "error");
      return;
    }
    setBusy(true);
    try {
      await downloadExport(exportKind, exportFrom, exportTo);
      push("Выгрузка скачана", "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const previewColumns = [
    { key: "row", title: "Строка", render: (row: ImportPreview["rows"][number]) => row.row },
    { key: "sku", title: "SKU", render: (row: ImportPreview["rows"][number]) => row.sku },
    {
      key: "value",
      title: "Значение",
      render: (row: ImportPreview["rows"][number]) => row.value ?? "—",
    },
    {
      key: "status",
      title: "Проверка",
      render: (row: ImportPreview["rows"][number]) =>
        row.ok ? (
          <Badge variant="success">OK</Badge>
        ) : (
          <Badge variant="warning">{row.message}</Badge>
        ),
    },
  ];

  const reportColumns = [
    { key: "row", title: "Строка", render: (row: ImportReport["errors"][number]) => row.row },
    { key: "message", title: "Ошибка", render: (row: ImportReport["errors"][number]) => row.message },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Обмен с 1С</h1>
      </header>

      <Card title="Импорт прайсов и остатков">
        <div className={styles.filters}>
          <label className={wizardStyles.field}>
            <span>Что импортируем</span>
            <select
              value={kind}
              onChange={(e) => {
                setKind(e.target.value as ImportKind);
                pickFile(null);
              }}
            >
              <option value="prices">{KIND_LABELS.prices}</option>
              <option value="stock">{KIND_LABELS.stock}</option>
            </select>
          </label>
          <Input
            label="Файл .xlsx (первая строка — заголовок)"
            type="file"
            accept=".xlsx"
            ref={fileInput}
            onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
          />
        </div>

        {kind === "prices" && (
          <label className={styles.checkbox}>
            <input
              type="checkbox"
              checked={archiveMissing}
              onChange={(e) => setArchiveMissing(e.target.checked)}
            />
            Архивировать товары, отсутствующие в прайсе
          </label>
        )}

        <div className={styles.actions}>
          <Button variant="accent" loading={busy} disabled={!file} onClick={runPreview}>
            Предпросмотр
          </Button>
          <Button
            variant="secondary"
            loading={busy}
            disabled={!file}
            onClick={runApply}
          >
            Применить без предпросмотра
          </Button>
          {file && (
            <Button variant="secondary" onClick={resetFile}>
              Сбросить файл
            </Button>
          )}
        </div>

        {preview && (
          <div className={wizardStyles.result}>
            <p className={styles.hint}>
              Проверено строк: {preview.rowsTotal}, с ошибками: {preview.errorsCount}.{" "}
              {previewIsClean(preview)
                ? "Файл готов к применению."
                : "Строки с ошибками будут пропущены при применении."}
            </p>
            <Table
              columns={previewColumns}
              data={preview.rows}
              rowKey={(row) => String(row.row)}
              empty="Строк нет"
            />
            <Button variant="accent" loading={busy} onClick={runApply}>
              Применить импорт
            </Button>
          </div>
        )}

        {report && (
          <div className={wizardStyles.result}>
            <p className={styles.hint}>
              Отчёт: всего {report.rowsTotal}, OK {report.rowsOk}, ошибок {report.rowsError}.
              Файл импорта зарегистрирован в integration_files.
            </p>
            {report.errors.length > 0 && (
              <Table
                columns={reportColumns}
                data={report.errors}
                rowKey={(row) => `${row.row}-${row.message}`}
                empty="Ошибок нет"
              />
            )}
          </div>
        )}
      </Card>

      <Card title="Выгрузка для 1С за период">
        <div className={styles.filters}>
          <label className={wizardStyles.field}>
            <span>Что выгружаем</span>
            <select
              value={exportKind}
              onChange={(e) =>
                setExportKind(e.target.value as "orders" | "invoices" | "waybills")
              }
            >
              <option value="orders">Заказы</option>
              <option value="invoices">Счета</option>
              <option value="waybills">Накладные (ТН/ТТН)</option>
            </select>
          </label>
          <Input
            label="С даты"
            type="date"
            value={exportFrom}
            onChange={(e) => setExportFrom(e.target.value)}
          />
          <Input
            label="По дату"
            type="date"
            value={exportTo}
            onChange={(e) => setExportTo(e.target.value)}
          />
        </div>
        <Button variant="accent" loading={busy} onClick={runExport}>
          Скачать .xlsx
        </Button>
      </Card>
    </div>
  );
}
