import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
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

  const download = (path: string, filename: string) => {
    downloadPdf(path, filename).catch((err) => push((err as Error).message, "error"));
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
          onClick={() => download(`/cabinet/invoices/${i.id}/pdf`, `${i.number}.pdf`)}
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
          onClick={() => download(`/cabinet/waybills/${w.id}/pdf`, `${w.number}.pdf`)}
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
        <nav className={styles.nav}>
          <Link to="/cabinet/catalog">Каталог</Link>
          <Link to="/cabinet/orders">Заказы</Link>
        </nav>
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
          empty="Счетов пока нет"
        />
      ) : (
        <Table<Waybill>
          columns={waybillColumns}
          data={waybills}
          rowKey={(w) => w.id}
          loading={loading}
          empty="Накладных пока нет"
        />
      )}
    </div>
  );
}
