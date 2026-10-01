import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Card, LinkButton } from "../../components/ui";
import { getCabinetSummary } from "../../api/cabinet";
import { listOrders } from "../../api/orders";
import { formatMoney } from "../../lib/format";
import type { OrderStatus } from "../../lib/orderStatus";
import { orderStatusBadge, orderStatusLabel } from "../../lib/orderStatus";
import { useAuthStore } from "../../store/authStore";
import styles from "./CabinetDashboardPage.module.scss";

/**
 * Дашборд кабинета клиента.
 *
 * <p>Раньше это была заглушка из заголовка и трёх ссылок: открыв кабинет, клиент
 * не видел ни своих заказов, ни состояния расчётов и был вынужден обходить
 * разделы наощупь. Теперь это рабочий экран — то, за чем B2B-клиент и приходит:
 * что в работе, сколько стоит, и есть ли долг (аудит 2026-09-30).
 */
export default function CabinetDashboardPage() {
  const user = useAuthStore((s) => s.user);
  const isLegal = user?.role === "CUSTOMER_LEGAL";

  const summary = useQuery({
    queryKey: ["cabinet", "summary"],
    queryFn: getCabinetSummary,
  });

  const activeOrders = useQuery({
    queryKey: ["cabinet", "active-orders"],
    queryFn: () => listOrders(0, 5),
  });

  const data = summary.data;
  const hasDebt = Number(data?.outstandingDebt ?? 0) > 0;
  const greeting = user?.fullName ?? user?.email ?? "";

  return (
    <div className="page">
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Здравствуйте, {greeting}</h1>
          <p className={styles.subtitle}>
            {data?.companyName ?? "Ваш кабинет"} — сводка по заказам и расчётам.
          </p>
        </div>
        <LinkButton to="/cabinet/catalog">В каталог</LinkButton>
      </header>

      {summary.isError && (
        <Card>
          <p className={styles.error}>
            Не удалось загрузить сводку.{" "}
            <button type="button" className={styles.retry} onClick={() => summary.refetch()}>
              Повторить
            </button>
          </p>
        </Card>
      )}

      <div className={styles.statGrid}>
        <StatCard
          label="Заказы в работе"
          value={String(data?.activeOrdersCount ?? 0)}
          hint={`на сумму ${formatMoney(Number(data?.activeOrdersTotal ?? 0))}`}
          loading={summary.isLoading}
        />
        <StatCard
          label="Ждут подтверждения"
          value={String(data?.awaitingConfirmationCount ?? 0)}
          hint={Number(data?.awaitingConfirmationCount ?? 0) > 0 ? "менеджер ещё не взял в работу" : "всё обработано"}
          loading={summary.isLoading}
        />
        {isLegal && (
          <>
            <StatCard
              label="Неоплаченные счета"
              value={String(data?.unpaidInvoicesCount ?? 0)}
              hint="требуют оплаты"
              loading={summary.isLoading}
            />
            <StatCard
              label="Текущий долг"
              value={formatMoney(Number(data?.outstandingDebt ?? 0))}
              hint={hasDebt ? "включая частично оплаченные" : "задолженности нет"}
              tone={hasDebt ? "warning" : "ok"}
              loading={summary.isLoading}
            />
          </>
        )}
      </div>

      <Card
        title="Последние заказы"
        actions={
          <Link to="/cabinet/orders" className={styles.cardLink}>
            Все заказы
          </Link>
        }
        bodyClassName={styles.listBody}
      >
        {activeOrders.isLoading && <p className={styles.muted}>Загружаем заказы…</p>}
        {!activeOrders.isLoading && (activeOrders.data?.items.length ?? 0) === 0 && (
          <div className={styles.empty}>
            <p>Заказов пока нет.</p>
            <LinkButton to="/cabinet/catalog">Собрать первый заказ</LinkButton>
          </div>
        )}
        {(activeOrders.data?.items.length ?? 0) > 0 && (
          <ul className={styles.list}>
            {activeOrders.data!.items.map((order) => (
              <li key={order.id} className={styles.listItem}>
                <div className={styles.listMain}>
                  <span className={styles.orderNumber}>{order.number}</span>
                  <span className={styles.muted}>{formatDate(order.createdAt)}</span>
                </div>
                <div className={styles.listAside}>
                  <span className={styles.amount}>{formatMoney(Number(order.totalAmount))}</span>
                  {orderStatusBadge(order.status as OrderStatus)}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {data?.lastOrderNumber && (
        <Card title="Последний заказ" bodyClassName={styles.lastOrder}>
          <div className={styles.lastOrderTop}>
            <span className={styles.orderNumber}>{data.lastOrderNumber}</span>
            {orderStatusBadge(data.lastOrderStatus as OrderStatus | undefined)}
          </div>
          <p className={styles.muted}>
            {orderStatusLabel(data.lastOrderStatus as OrderStatus | undefined)}
            {data.lastOrderAt ? ` · ${formatDate(data.lastOrderAt)}` : ""}
          </p>
          <div className={styles.actions}>
            <LinkButton to="/cabinet/orders" variant="secondary">
              Открыть заказы
            </LinkButton>
          </div>
        </Card>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  hint,
  tone = "plain",
  loading,
}: {
  label: string;
  value: string;
  hint: string;
  tone?: "plain" | "ok" | "warning";
  loading?: boolean;
}) {
  return (
    <Card bodyClassName={styles.stat}>
      <span className={styles.statLabel}>{label}</span>
      <span
        className={tone === "warning" ? styles.statValueWarning : styles.statValue}
        aria-busy={loading || undefined}
      >
        {loading ? "…" : value}
      </span>
      <span className={styles.statHint}>{hint}</span>
    </Card>
  );
}

function formatDate(iso?: string | null): string {
  if (!iso) return "";
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day}.${month}.${year}`;
}