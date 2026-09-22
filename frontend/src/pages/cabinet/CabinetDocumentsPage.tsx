import { useCallback, useEffect, useState } from "react";
import { Button, Card, Table, Tabs, useToast } from "../../components/ui";
import {
  downloadPdf,
  listCabinetInvoices,
  listCabinetWaybills,
  type Invoice,
  type Waybill,
} from "../../api/documents";
import { formatDocDate, invoiceStatusBadge, waybillTypeLabel } from "../../lib/documents";
import { formatMoney } from "../../lib/format";
import styles from "./CabinetDocumentsPage.module.scss";

/**
 * Кабинет юрлица: счета и накладные своей компании, скачивание PDF (S27).
 * Физлицу документы не доступны (матрица 2.1) — роут под RoleGuard.
 */
export default function CabinetDocumentsPage() {
  const { push } = useToast();
  const [tab, setTab] = useState("invoices");
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [waybills, setWaybills] = useState<Waybill[]>([]);
  const [loading, setLoading] = useState(true);
  // plan-03: одна PDF-загрузка за раз — повторный клик по кнопке до ответа невозможен
  const [pdfBusyId, setPdfBusyId] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([listCabinetInvoices(), listCabinetWaybills()])
      .then(([invoicePage, waybillPage]) => {
        setInvoices(invoicePage.items);
        setWaybills(waybillPage.items);
      })
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const download = async (id: string, path: string, filename: string) => {
    if (pdfBusyId) return;
    setPdfBusyId(id);
    try {
      await downloadPdf(path, filename);
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setPdfBusyId(null);
    }
  };

  const invoiceColumns = [
    { key: "number", title: "Счёт", render: (i: Invoice) => <strong>{i.displayNumber}</strong> },
    { key: "date", title: "Дата", render: (i: Invoice) => formatDocDate(i.issueDate) },
    { key: "status", title: "Статус", render: (i: Invoice) => invoiceStatusBadge(i.status) },
    {
      key: "total",
      title: "Сумма",
      align: "right" as const,
      render: (i: Invoice) => formatMoney(i.totalAmount),
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (i: Invoice) => (
        <Button
          size="sm"
          variant="secondary"
          loading={pdfBusyId === i.id}
          onClick={() => download(i.id, `/cabinet/invoices/${i.id}/pdf`, `${i.number}.pdf`)}
        >
          PDF
        </Button>
      ),
    },
  ];

  const waybillColumns = [
    { key: "number", title: "Накладная", render: (w: Waybill) => <strong>{w.displayNumber}</strong> },
    { key: "type", title: "Тип", render: (w: Waybill) => waybillTypeLabel(w.type) },
    { key: "date", title: "Дата", render: (w: Waybill) => formatDocDate(w.issueDate) },
    {
      key: "total",
      title: "Сумма",
      align: "right" as const,
      render: (w: Waybill) => formatMoney(w.totalAmount),
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (w: Waybill) => (
        <Button
          size="sm"
          variant="secondary"
          loading={pdfBusyId === w.id}
          onClick={() => download(w.id, `/cabinet/waybills/${w.id}/pdf`, `${w.number}.pdf`)}
        >
          PDF
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Документы</h1>
      </header>

      <Card className={styles.filters}>
        <Tabs
          items={[
            { key: "invoices", label: "Счета" },
            { key: "waybills", label: "Накладные" },
          ]}
          active={tab}
          onChange={setTab}
        />
      </Card>

      {tab === "invoices" ? (
        <Table<Invoice>
          columns={invoiceColumns}
          data={invoices}
          rowKey={(i) => i.id}
          loading={loading}
          empty="Счетов пока нет — счёт выставит менеджер после подтверждения заказа"
        />
      ) : (
        <Table<Waybill>
          columns={waybillColumns}
          data={waybills}
          rowKey={(w) => w.id}
          loading={loading}
          empty="Накладных пока нет — накладную оформит менеджер после подтверждения заказа"
        />
      )}
    </div>
  );
}
