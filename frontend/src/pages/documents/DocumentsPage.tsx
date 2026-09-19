import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Card, Modal, Select, Table, Tabs, useToast } from "../../components/ui";
import {
  createInvoice,
  createWaybill,
  downloadPdf,
  issueInvoice,
  listInvoices,
  listWaybills,
  type Invoice,
  type Waybill,
  type WaybillType,
} from "../../api/documents";
import { listOrders, type Order } from "../../api/orders";
import {
  formatDocDate,
  invoiceStatusBadge,
  waybillTypeLabel,
} from "../../lib/documents";
import { formatMoney } from "../../lib/format";
import { orderStatusLabel, type OrderStatus } from "../../lib/orderStatus";
import styles from "./DocumentsPage.module.scss";

const INVOICE_STATUS_OPTIONS = [
  { value: "", label: "Все статусы" },
  { value: "DRAFT", label: "Черновик" },
  { value: "ISSUED", label: "Выпущен" },
  { value: "PARTIALLY_PAID", label: "Частично оплачен" },
  { value: "PAID", label: "Оплачен" },
  { value: "CANCELLED", label: "Отменён" },
];

/**
 * Документы бэк-офиса (S27): счета и накладные, выставление счёта из заказа,
 * оформление ТН/ТТН, скачивание PDF. Одна страница для ADMIN/ACCOUNTANT
 * (/admin/documents) и MANAGER (/manager/documents) — скоупинг на бэкенде.
 */
export default function DocumentsPage() {
  const { push } = useToast();

  const [tab, setTab] = useState("invoices");
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [waybills, setWaybills] = useState<Waybill[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState<"" | WaybillType>("");
  const [loading, setLoading] = useState(true);

  const [orderModal, setOrderModal] = useState<null | "invoice" | "waybill">(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [selectedOrder, setSelectedOrder] = useState("");
  const [waybillType, setWaybillType] = useState<WaybillType>("TTN");
  const [vehicle, setVehicle] = useState("");
  const [driver, setDriver] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([listInvoices(), listWaybills()])
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

  const openOrderModal = (kind: "invoice" | "waybill") => {
    setOrderModal(kind);
    setSelectedOrder("");
    listOrders(0, 30)
      .then((page) => setOrders(page.items))
      .catch((err) => push((err as Error).message, "error"));
  };

  const createFromOrder = async () => {
    if (!selectedOrder) return;
    setSubmitting(true);
    try {
      if (orderModal === "invoice") {
        const invoice = await createInvoice(selectedOrder);
        push(`Счёт ${invoice.displayNumber} создан (черновик)`, "success");
      } else {
        const waybill = await createWaybill({
          orderId: selectedOrder,
          type: waybillType,
          vehicle: vehicle || undefined,
          driver: driver || undefined,
        });
        push(`Накладная ${waybill.displayNumber} оформлена`, "success");
      }
      setOrderModal(null);
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  const issue = async (invoice: Invoice) => {
    try {
      await issueInvoice(invoice.id);
      push(`Счёт ${invoice.displayNumber} выпущен, PDF готов`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const filteredInvoices = useMemo(
    () => (statusFilter ? invoices.filter((i) => i.status === statusFilter) : invoices),
    [invoices, statusFilter],
  );
  const filteredWaybills = useMemo(
    () => (typeFilter ? waybills.filter((w) => w.type === typeFilter) : waybills),
    [waybills, typeFilter],
  );

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
        <div className={styles.rowActions}>
          {i.status === "DRAFT" && (
            <Button size="sm" variant="accent" onClick={() => issue(i)}>
              Выпустить
            </Button>
          )}
          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              downloadPdf(`/invoices/${i.id}/pdf`, `${i.number}.pdf`).catch((err) =>
                push((err as Error).message, "error"),
              )
            }
          >
            PDF
          </Button>
        </div>
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
      key: "weight",
      title: "Масса, кг",
      align: "right" as const,
      render: (w: Waybill) => (w.totalWeight ? w.totalWeight.toFixed(3) : "—"),
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (w: Waybill) => (
        <Button
          size="sm"
          variant="secondary"
          onClick={() =>
            downloadPdf(`/waybills/${w.id}/pdf`, `${w.number}.pdf`).catch((err) =>
              push((err as Error).message, "error"),
            )
          }
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
        <div className={styles.headerActions}>
          <Button variant="accent" onClick={() => openOrderModal("invoice")}>
            Выставить счёт из заказа
          </Button>
          <Button onClick={() => openOrderModal("waybill")}>Оформить накладную</Button>
        </div>
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
        {tab === "invoices" ? (
          <Select
            value={statusFilter}
            options={INVOICE_STATUS_OPTIONS}
            onChange={(e) => setStatusFilter(e.target.value)}
            className={styles.filterSelect}
          />
        ) : (
          <Select
            value={typeFilter}
            placeholder="Все типы"
            options={[
              { value: "TTN", label: "ТТН" },
              { value: "TN", label: "ТН" },
            ]}
            onChange={(e) => setTypeFilter(e.target.value as "" | WaybillType)}
            className={styles.filterSelect}
          />
        )}
      </Card>

      {tab === "invoices" ? (
        <Table<Invoice>
          columns={invoiceColumns}
          data={filteredInvoices}
          rowKey={(i) => i.id}
          loading={loading}
          empty="Счетов пока нет — выставите счёт из заказа"
        />
      ) : (
        <Table<Waybill>
          columns={waybillColumns}
          data={filteredWaybills}
          rowKey={(w) => w.id}
          loading={loading}
          empty="Накладных пока нет"
        />
      )}

      <Modal
        open={orderModal !== null}
        title={orderModal === "invoice" ? "Выставить счёт из заказа" : "Оформить накладную"}
        onClose={() => setOrderModal(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOrderModal(null)}>
              Отмена
            </Button>
            <Button
              variant="accent"
              loading={submitting}
              disabled={!selectedOrder}
              onClick={createFromOrder}
            >
              {orderModal === "invoice" ? "Создать счёт" : "Оформить"}
            </Button>
          </>
        }
      >
        <div className={styles.modalBody}>
          <Select
            value={selectedOrder}
            placeholder="Выберите заказ…"
            options={orders.map((o) => ({
              value: o.id,
              label: `${o.number} — ${formatMoney(o.totalAmount)} (${orderStatusLabel(o.status as OrderStatus)})`,
            }))}
            onChange={(e) => setSelectedOrder(e.target.value)}
          />
          {orderModal === "waybill" && (
            <>
              <Select
                value={waybillType}
                options={[
                  { value: "TTN", label: "ТТН (товарно-транспортная)" },
                  { value: "TN", label: "ТН (товарная)" },
                ]}
                onChange={(e) => setWaybillType(e.target.value as WaybillType)}
              />
              <input
                className={styles.input}
                placeholder="Автомобиль (например, AB 1234-5)"
                value={vehicle}
                onChange={(e) => setVehicle(e.target.value)}
              />
              <input
                className={styles.input}
                placeholder="Водитель"
                value={driver}
                onChange={(e) => setDriver(e.target.value)}
              />
            </>
          )}
          <p className={styles.hint}>
            {orderModal === "invoice"
              ? "Счёт создаётся черновиком: снапшоты реквизитов и позиций зафиксируются на момент создания."
              : "Накладная создаётся окончательным документом: PDF формируется сразу."}
          </p>
        </div>
      </Modal>
    </div>
  );
}
