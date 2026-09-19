import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge, Button, Card, Table } from "../../components/ui";
import { getOperationsDashboard, type OperationsDashboard } from "../../api/adminOperations";
import styles from "./AdminDashboardPage.module.scss";

type LowStockItem = OperationsDashboard["lowStock"][number];
type OverdueTask = OperationsDashboard["overdueTasks"][number];

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatMoney(value: number) {
  return `${value.toFixed(2)} BYN`;
}

/** Дашборд процессов (S38): операционный центр — что требует внимания сегодня. */
export default function AdminDashboardPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["operations-dashboard"],
    queryFn: getOperationsDashboard,
    refetchInterval: 60_000,
  });

  if (isError) {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Операционный центр</h1>
        <p>Не удалось загрузить дашборд.</p>
        <Button variant="secondary" onClick={() => void refetch()}>
          Повторить
        </Button>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Операционный центр</h1>
      <p className={styles.subtitle}>Что требует внимания сегодня</p>

      <div className={styles.tiles}>
        <Tile
          label="Новые заказы за сегодня"
          value={data?.ordersToday}
          loading={isLoading}
          to="/admin/documents"
        />
        <Tile
          label="Заявки с сайта за сегодня"
          value={data?.requestsToday}
          loading={isLoading}
          to="/manager/inbox"
        />
        <Tile
          label="Неоплаченные счета"
          value={data?.unpaidInvoices.count}
          hint={
            data && data.unpaidInvoices.count > 0
              ? `К оплате: ${formatMoney(data.unpaidInvoices.outstanding)}`
              : undefined
          }
          loading={isLoading}
          to="/admin/documents"
          accent={!!data && data.unpaidInvoices.count > 0}
        />
      </div>

      <Card
        title="Позиции ниже min_stock"
        actions={
          <Link to="/warehouse/balance">
            <Button variant="secondary" size="sm">
              Остатки склада
            </Button>
          </Link>
        }
      >
        <Table<LowStockItem>
          columns={[
            { key: "sku", title: "SKU", render: (row) => row.sku },
            { key: "name", title: "Товар", render: (row) => row.productName },
            {
              key: "available",
              title: "Доступно",
              render: (row) => <Badge variant="warning">{row.available}</Badge>,
            },
            { key: "minStock", title: "Мин. остаток", render: (row) => row.minStock },
          ]}
          data={data?.lowStock ?? []}
          rowKey={(row) => row.productId}
          loading={isLoading}
          empty="Все позиции выше минимального остатка"
        />
      </Card>

      <Card title="Просроченные задачи менеджеров">
        <Table<OverdueTask>
          columns={[
            { key: "title", title: "Задача", render: (row) => row.title },
            { key: "assignee", title: "Исполнитель", render: (row) => row.assigneeName },
            {
              key: "dueDate",
              title: "Срок",
              render: (row) => <Badge variant="warning">{formatDateTime(row.dueDate)}</Badge>,
            },
          ]}
          data={data?.overdueTasks ?? []}
          rowKey={(row) => row.id}
          loading={isLoading}
          empty="Просроченных задач нет"
        />
      </Card>
    </div>
  );
}

function Tile({
  label,
  value,
  hint,
  loading,
  to,
  accent = false,
}: {
  label: string;
  value: number | undefined;
  hint?: string;
  loading: boolean;
  to: string;
  accent?: boolean;
}) {
  return (
    <Link to={to}>
      <div className={[styles.tile, accent ? styles.tileAccent : ""].filter(Boolean).join(" ")}>
        <span className={styles.tileValue}>{loading ? "…" : (value ?? 0)}</span>
        <span className={styles.tileLabel}>{label}</span>
        {hint && <span className={styles.tileHint}>{hint}</span>}
      </div>
    </Link>
  );
}
