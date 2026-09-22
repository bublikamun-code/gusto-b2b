import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Card, Table, Tabs, useToast } from "../../components/ui";
import { assignLead, listLeads, type Lead } from "../../api/crm";
import { listManagerOrders, takeOrder, type Order } from "../../api/orders";
import { listTasks, type CrmTask } from "../../api/crm";
import { orderStatusBadge, type OrderStatus } from "../../lib/orderStatus";
import { isStale } from "../../lib/inbox";
import { useAuthStore } from "../../store/authStore";
import styles from "./CrmInboxPage.module.scss";

/**
 * «Единое окно входящих» (S30, цель 3): пул заказов без менеджера (2.7),
 * лиды без назначения, просроченные задачи; счётчик «без ответа > 24 ч».
 */
export default function CrmInboxPage() {
  const { push } = useToast();
  const user = useAuthStore((s) => s.user);
  const [tab, setTab] = useState("orders");

  const [orders, setOrders] = useState<Order[]>([]);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [tasks, setTasks] = useState<CrmTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([listManagerOrders({ scope: "unassigned", size: 50 }), listLeads({ scope: "unassigned", size: 50 }), listTasks("overdue")])
      .then(([orderPage, leadPage, taskPage]) => {
        setOrders(orderPage.items);
        setLeads(leadPage.items);
        setTasks(taskPage.items);
      })
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const staleCount =
    orders.filter((o) => isStale(o.createdAt)).length +
    leads.filter((l) => isStale(l.createdAt)).length;

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

  const takeLead = async (lead: Lead) => {
    if (!user) return;
    setBusy(lead.id);
    try {
      await assignLead(lead.id, user.id);
      push(`Лид «${lead.name}» закреплён за вами`, "success");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setBusy(null);
    }
  };

  const orderColumns = [
    { key: "number", title: "Заказ", render: (o: Order) => <strong>{o.number}</strong> },
    {
      key: "created",
      title: "Создан",
      render: (o: Order) =>
        new Date(o.createdAt).toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }),
    },
    { key: "status", title: "Статус", render: (o: Order) => orderStatusBadge(o.status as OrderStatus) },
    {
      key: "stale",
      title: "Ожидание",
      render: (o: Order) =>
        isStale(o.createdAt) ? <Badge variant="warning">без ответа &gt; 24 ч</Badge> : <Badge variant="neutral">свежий</Badge>,
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (o: Order) => (
        <Button variant="accent" loading={busy === o.id} onClick={() => take(o)}>
          Взять в работу
        </Button>
      ),
    },
  ];

  const leadColumns = [
    { key: "name", title: "Лид", render: (l: Lead) => <strong>{l.name}</strong> },
    { key: "company", title: "Компания", render: (l: Lead) => l.companyName ?? "—" },
    { key: "phone", title: "Телефон", render: (l: Lead) => l.phone ?? "—" },
    {
      key: "stale",
      title: "Ожидание",
      render: (l: Lead) =>
        isStale(l.createdAt) ? <Badge variant="warning">без ответа &gt; 24 ч</Badge> : <Badge variant="neutral">свежий</Badge>,
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (l: Lead) => (
        <Button variant="accent" loading={busy === l.id} onClick={() => takeLead(l)}>
          Взять себе
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Единое окно входящих</h1>
      </header>

      <div className={styles.counters}>
        <Card className={styles.counter}>
          <span className={styles.counterValue}>{staleCount}</span>
          <span>без ответа &gt; 24 ч</span>
        </Card>
        <Card className={styles.counter}>
          <span className={styles.counterValue}>{tasks.length}</span>
          <span>просроченных задач</span>
        </Card>
      </div>

      <Card className={styles.tabsCard}>
        <Tabs
          items={[
            { key: "orders", label: `Заказы (${orders.length})` },
            { key: "leads", label: `Лиды (${leads.length})` },
            { key: "tasks", label: `Задачи (${tasks.length})` },
          ]}
          active={tab}
          onChange={setTab}
        />
      </Card>

      {tab === "orders" && (
        <Table<Order>
          columns={orderColumns}
          data={orders}
          rowKey={(o) => o.id}
          loading={loading}
          empty="Пул пуст — все заказы разобраны"
        />
      )}

      {tab === "leads" && (
        <Table<Lead>
          columns={leadColumns}
          data={leads}
          rowKey={(l) => l.id}
          loading={loading}
          empty="Неразобранных лидов нет"
        />
      )}

      {tab === "tasks" && (
        <Card className={styles.tasks}>
          {tasks.length === 0 && <p className={styles.empty}>Просроченных задач нет</p>}
          {tasks.map((task) => (
            <div key={task.id} className={styles.taskRow}>
              <div>
                <strong>{task.title}</strong>
                <span className={styles.taskMeta}>
                  {task.dueDate ? `срок: ${new Date(task.dueDate).toLocaleString("ru-RU")}` : "без срока"}
                </span>
              </div>
              <Badge variant="warning">просрочена</Badge>
            </div>
          ))}
        </Card>
      )}
    </div>
  );
}
