import { useCallback, useEffect, useState } from "react";
import { Button, Card, Input, Pagination, Table } from "../../components/ui";
import {
  searchAudit,
  type AuditEntry,
  type AuditFilters,
} from "../../api/adminOperations";
import styles from "./AdminPages.module.scss";

const PAGE_SIZE = 20;

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Журнал аудита (S38): фильтры по действию, объекту, периоду; только ADMIN. */
export default function AdminAuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<AuditFilters>({});

  const load = useCallback(() => {
    setLoading(true);
    searchAudit({ ...filters, page, size: PAGE_SIZE })
      .then((result) => {
        setEntries(result.items);
        setTotal(result.total);
      })
      .catch(() => {
        setEntries([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [filters, page]);

  useEffect(() => {
    load();
  }, [load]);

  const applyFilters = () => {
    setPage(0);
    load();
  };

  const columns = [
    {
      key: "createdAt",
      title: "Когда",
      render: (e: AuditEntry) => formatDateTime(e.createdAt),
    },
    {
      key: "actor",
      title: "Кто",
      render: (e: AuditEntry) => e.actorName ?? e.actorEmail ?? "—",
    },
    {
      key: "action",
      title: "Действие",
      render: (e: AuditEntry) => <strong>{e.action}</strong>,
    },
    {
      key: "target",
      title: "Объект",
      render: (e: AuditEntry) =>
        e.targetType ? `${e.targetType}${e.targetId ? ` · ${e.targetId.slice(0, 8)}` : ""}` : "—",
    },
    {
      key: "details",
      title: "Детали",
      render: (e: AuditEntry) => {
        const details = e.after ?? e.before;
        if (!details) return "—";
        const parts = Object.entries(details)
          .slice(0, 4)
          .map(([key, value]) => `${key}: ${JSON.stringify(value)}`);
        return parts.join(", ") || "—";
      },
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Журнал аудита</h1>
      </header>

      <Card>
        <div className={styles.filters}>
          <Input
            label="Действие (префикс)"
            placeholder="SETTINGS_UPDATE"
            value={filters.action ?? ""}
            onChange={(e) => setFilters({ ...filters, action: e.target.value || undefined })}
          />
          <Input
            label="Тип объекта"
            placeholder="settings"
            value={filters.targetType ?? ""}
            onChange={(e) => setFilters({ ...filters, targetType: e.target.value || undefined })}
          />
          <Input
            label="С даты"
            type="date"
            value={filters.dateFrom ?? ""}
            onChange={(e) => setFilters({ ...filters, dateFrom: e.target.value || undefined })}
          />
          <Input
            label="По дату"
            type="date"
            value={filters.dateTo ?? ""}
            onChange={(e) => setFilters({ ...filters, dateTo: e.target.value || undefined })}
          />
          <div className={styles.actions}>
            <Button variant="accent" onClick={applyFilters} loading={loading}>
              Найти
            </Button>
            <Button
              variant="secondary"
              onClick={() => {
                setFilters({});
                setPage(0);
              }}
            >
              Сбросить
            </Button>
          </div>
        </div>
      </Card>

      <Table<AuditEntry>
        columns={columns}
        data={entries}
        rowKey={(e) => e.id}
        loading={loading}
        empty="Записей не найдено — измените фильтры"
      />

      <div className={styles.pagination}>
        <Pagination
          page={page + 1}
          size={PAGE_SIZE}
          total={total}
          onChange={(next) => setPage(next - 1)}
        />
      </div>
    </div>
  );
}
