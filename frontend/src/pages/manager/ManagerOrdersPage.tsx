import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Button, Card, Pagination, Select, Table, Tabs, useToast } from "../../components/ui";
import {
  changeOrderStatus,
  listManagerOrders,
  takeOrder,
  type ManagerOrderFilters,
  type Order,
} from "../../api/orders";
import {
  ORDER_STATUSES,
  allowedNextStatuses,
  orderStatusBadge,
  orderStatusLabel,
  type OrderStatus,
} from "../../lib/orderStatus";
import { formatMoney } from "../../lib/format";
import { useAuthStore } from "../../store/authStore";
import styles from "./ManagerOrdersPage.module.scss";

const SCOPES = [
  { key: "mine", label: "Мои заказы" },
  { key: "unassigned", label: "Не назначено" },
];

const PAGE_SIZE = 20;

export default function ManagerOrdersPage() {
  const { push } = useToast();
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";

  const [scope, setScope] = useState<string>("unassigned");
  const [statusFilter, setStatusFilter] = useState<"" | OrderStatus>("");
  const [orders, setOrders] = useState<Order[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [nextStatus, setNextStatus] = useState<Record<string, OrderStatus>>({});

  const load = useCallback(() => {
    setLoading(true);
    const filters: ManagerOrderFilters = { scope: scope as ManagerOrderFilters["scope"], page, size: PAGE_SIZE };
    if (statusFilter) filters.status = statusFilter;
    listManagerOrders(filters)
      .then((result) => {
        setOrders(result.items);
        setTotal(result.total);
      })
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [scope, page, statusFilter, push]);

  useEffect(() => {
    load();
  }, [load]);

  const take = async (order: Order) => {
    setBusy(order.id);
    try {
      await takeOrder(order.id);
      push(`Заказ ${order.number} назначен вам`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(null);
    }
  };

  const applyStatus = async (order: Order) => {
    const target = nextStatus[order.id];
    if (!target) return;
    setBusy(order.id);
    try {
      await changeOrderStatus(order.id, target);
      push(`Заказ ${order.number} → ${orderStatusLabel(target)}`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(null);
    }
  };

  const columns = useMemo(() => {
    const base = [
      { key: "number", title: "Номер", render: (o: Order) => <strong>{o.number}</strong> },
      {
        key: "createdAt",
        title: "Дата",
        render: (o: Order) =>
          new Date(o.createdAt).toLocaleString("ru-RU", {
            day: "2-digit",
            month: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
          }),
      },
      { key: "status", title: "Статус", render: (o: Order) => orderStatusBadge(o.status as OrderStatus) },
      {
        key: "recipient",
        title: "Получатель",
        render: (o: Order) => o.recipientName ?? "—",
      },
      {
        key: "total",
        title: "Сумма",
        align: "right" as const,
        render: (o: Order) => formatMoney(o.totalAmount),
      },
    ];
    const actions = {
      key: "actions",
      title: "Действия",
      align: "right" as const,
      render: (o: Order) => {
        const next = allowedNextStatuses(o.status as OrderStatus);
        if (scope === "unassigned") {
          return (
            <Button
              variant="accent"
              loading={busy === o.id}
              disabled={o.managerId != null}
              onClick={() => take(o)}
            >
              Взять в работу
            </Button>
          );
        }
        if (next.length === 0) return <span className={styles.terminal}>—</span>;
        return (
          <div className={styles.rowActions}>
            <Select
              value={nextStatus[o.id] ?? ""}
              placeholder="Статус…"
              options={next.map((status) => ({ value: status, label: orderStatusLabel(status) }))}
              onChange={(e) =>
                setNextStatus((prev) => ({
                  ...prev,
                  [o.id]: e.target.value as OrderStatus,
                }))
              }
            />
            <Button loading={busy === o.id} disabled={!nextStatus[o.id]} onClick={() => applyStatus(o)}>
              Применить
            </Button>
          </div>
        );
      },
    };
    return [...base, actions];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope, busy, nextStatus]);

  const tabs = isAdmin ? [...SCOPES, { key: "all", label: "Все заказы" }] : SCOPES;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Заказы</h1>
        <nav className={styles.nav}>
          <Link to="/manager/inbox">Единое окно</Link>
          <Link to="/manager/orders/new">Заказ от имени клиента</Link>
          <Link to="/warehouse">Склад</Link>
        </nav>
      </header>

      <Card className={styles.filters}>
        <Tabs items={tabs} active={scope} onChange={(key) => { setScope(key); setPage(0); }} />
        <Select
          value={statusFilter}
          placeholder="Все статусы"
          options={ORDER_STATUSES.map((status) => ({
            value: status,
            label: orderStatusLabel(status),
          }))}
          onChange={(e) => {
            setStatusFilter(e.target.value as "" | OrderStatus);
            setPage(0);
          }}
          className={styles.statusFilter}
        />
      </Card>

      <Table<Order>
        columns={columns}
        data={orders}
        rowKey={(o) => o.id}
        loading={loading}
        empty={scope === "unassigned" ? "Пул пуст — все заказы разобраны" : "Заказов нет"}
      />

      <Pagination page={page + 1} size={PAGE_SIZE} total={total} onChange={(p) => setPage(p - 1)} />
    </div>
  );
}
