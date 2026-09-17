import { useCallback, useEffect, useState } from "react";
import { Button, Input, Table, useToast } from "../../components/ui";
import {
  getMovementsReport,
  getToOrderReport,
  type MovementRow,
  type ToOrderRow,
} from "../../api/warehouse";
import styles from "./WarehousePages.module.scss";

const dayAgo = () => new Date(Date.now() - 7 * 24 * 3600 * 1000).toISOString().slice(0, 10);
const now = () => new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 10);

const MOVEMENT_LABELS: Record<string, string> = {
  INCOMING: "Приход",
  OUTGOING: "Расход",
  ADJUSTMENT: "Корректировка",
  RESERVE: "Резерв",
  RELEASE: "Освобождение",
};

export default function WarehouseReportsPage() {
  const { push } = useToast();
  const [toOrder, setToOrder] = useState<ToOrderRow[]>([]);
  const [movements, setMovements] = useState<MovementRow[]>([]);
  const [from, setFrom] = useState(dayAgo());
  const [to, setTo] = useState(now());
  const [loading, setLoading] = useState(false);

  const loadMovements = useCallback(
    (fromValue: string, toValue: string) => {
      setLoading(true);
      getMovementsReport(`${fromValue}T00:00:00Z`, `${toValue}T23:59:59Z`)
        .then(setMovements)
        .catch((err) => push((err as Error).message, "error"))
        .finally(() => setLoading(false));
    },
    [push],
  );

  useEffect(() => {
    getToOrderReport()
      .then(setToOrder)
      .catch((err) => push((err as Error).message, "error"));
    loadMovements(from, to);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toOrderColumns = [
    { key: "sku", title: "Артикул" },
    { key: "productName", title: "Товар" },
    { key: "minStock", title: "Минимум", align: "right" as const },
    {
      key: "available",
      title: "Доступно",
      align: "right" as const,
      render: (row: ToOrderRow) => <span className={styles.deficit}>{row.available}</span>,
    },
  ];

  const movementColumns = [
    { key: "createdAt", title: "Время", render: (row: MovementRow) => row.createdAt.slice(0, 16).replace("T", " ") },
    { key: "sku", title: "Артикул" },
    { key: "productName", title: "Товар" },
    { key: "locationName", title: "Склад" },
    { key: "type", title: "Тип", render: (row: MovementRow) => MOVEMENT_LABELS[row.type] ?? row.type },
    {
      key: "quantity",
      title: "Кол-во",
      align: "right" as const,
      render: (row: MovementRow) => (
        <span className={row.quantity < 0 ? styles.negative : undefined}>{row.quantity}</span>
      ),
    },
    { key: "note", title: "Примечание" },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
      <div className={styles.page}>
        <h1 className={styles.title}>Что заказать (ниже минимума)</h1>
        <Table
          columns={toOrderColumns}
          data={toOrder}
          rowKey={(row) => row.productId}
          empty="Дефицита нет — всё выше минимального остатка"
        />
      </div>

      <div className={styles.page}>
        <h1 className={styles.title}>Движения за период</h1>
        <div className={styles.toolbar}>
          <Input
            label="С"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
          <Input
            label="По"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
          <span>журнал включает резервы и корректировки</span>
          <Button
            variant="secondary"
            onClick={() => loadMovements(from, to)}
            loading={loading}
          >
            Обновить
          </Button>
        </div>
        <Table
          columns={movementColumns}
          data={movements}
          rowKey={(row) => `${row.productId}-${row.createdAt}-${row.type}`}
          loading={loading}
          empty="Движений за период нет"
        />
      </div>
    </div>
  );
}
