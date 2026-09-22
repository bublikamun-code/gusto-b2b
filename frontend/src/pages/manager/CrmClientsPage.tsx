import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Input, Modal, Table, useToast } from "../../components/ui";
import { addNote, listNotes, listTasks, type CrmNote, type CrmTask } from "../../api/crm";
import { listManagerCompanies, type ManagerCompany } from "../../api/orders";
import { listOrders, type Order } from "../../api/orders";
import { formatMoney } from "../../lib/format";
import { orderStatusBadge, type OrderStatus } from "../../lib/orderStatus";
import styles from "./CrmClientsPage.module.scss";

/**
 * Клиенты менеджера (S30): список закреплённых компаний, карточка компании —
 * заказы, задачи, заметки + добавление заметки (история взаимодействия).
 */
export default function CrmClientsPage() {
  const { push } = useToast();

  const [companies, setCompanies] = useState<ManagerCompany[]>([]);
  const [selected, setSelected] = useState<ManagerCompany | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [tasks, setTasks] = useState<CrmTask[]>([]);
  const [notes, setNotes] = useState<CrmNote[]>([]);
  const [noteBody, setNoteBody] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingNote, setSavingNote] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([listManagerCompanies(), listOrders(0, 50), listTasks("mine")])
      .then(([companyPage, orderPage, taskPage]) => {
        setCompanies(companyPage.items);
        setOrders(orderPage.items);
        setTasks(taskPage.items);
      })
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const openCard = (company: ManagerCompany) => {
    setSelected(company);
    setNoteBody("");
    listNotes(company.id)
      .then((page) => setNotes(page.items))
      .catch((err) => push((err as Error).message, "error"));
  };

  const saveNote = async () => {
    if (!selected || !noteBody.trim() || savingNote) return;
    setSavingNote(true);
    try {
      await addNote(selected.id, noteBody.trim());
      setNotes((await listNotes(selected.id)).items);
      setNoteBody("");
      push("Заметка добавлена", "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSavingNote(false);
    }
  };

  const companyOrders = selected ? orders.filter((o) => o.customerCompanyId === selected.id) : [];
  const companyTasks = selected ? tasks.filter((t) => t.companyId === selected.id) : [];

  const columns = [
    { key: "name", title: "Компания", render: (c: ManagerCompany) => <strong>{c.name}</strong> },
    { key: "unp", title: "УНП", render: (c: ManagerCompany) => c.unp ?? "—" },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (c: ManagerCompany) => (
        <Button size="sm" variant="secondary" onClick={() => openCard(c)}>
          Открыть
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Мои клиенты</h1>
      </header>

      <Table<ManagerCompany>
        columns={columns}
        data={companies}
        rowKey={(c) => c.id}
        loading={loading}
        empty="Закреплённых компаний пока нет"
      />

      <Modal open={!!selected} onClose={() => setSelected(null)} title={selected?.name}>
        {selected && (
          <>
            <div className={styles.modalHead}>
              {selected.unp && <Badge variant="neutral">УНП {selected.unp}</Badge>}
              <Button variant="secondary" onClick={() => setSelected(null)}>
                Закрыть
              </Button>
            </div>

            <div className={styles.section}>
              <h3>Заказы</h3>
              {companyOrders.length === 0 && <p className={styles.empty}>Заказов нет</p>}
              <ul className={styles.list}>
                {companyOrders.map((o) => (
                  <li key={o.id}>
                    {o.number} — {formatMoney(o.totalAmount)}{" "}
                    {orderStatusBadge(o.status as OrderStatus)}
                  </li>
                ))}
              </ul>
            </div>

            <div className={styles.section}>
              <h3>Задачи</h3>
              {companyTasks.length === 0 && <p className={styles.empty}>Задач нет</p>}
              <ul className={styles.list}>
                {companyTasks.map((t) => (
                  <li key={t.id}>
                    {t.title} {t.overdue && <Badge variant="warning">просрочена</Badge>}
                  </li>
                ))}
              </ul>
            </div>

            <div className={styles.section}>
              <h3>Заметки</h3>
              <div className={styles.noteForm}>
                <Input
                  className={styles.input}
                  placeholder="Новая заметка о клиенте…"
                  value={noteBody}
                  onChange={(e) => setNoteBody(e.target.value)}
                />
                <Button
                  size="sm"
                  variant="accent"
                  loading={savingNote}
                  disabled={!noteBody.trim()}
                  onClick={saveNote}
                >
                  Добавить
                </Button>
              </div>
              <ul className={styles.list}>
                {notes.map((n) => (
                  <li key={n.id}>
                    <span className={styles.noteBody}>{n.body}</span>
                    <span className={styles.noteMeta}>
                      {n.authorName ?? ""} · {new Date(n.createdAt).toLocaleString("ru-RU")}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}
