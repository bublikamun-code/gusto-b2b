import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Input, Modal, Table, useToast } from "../../components/ui";
import {
  createSupplier,
  deactivateSupplier,
  listSuppliers,
  updateSupplier,
  type Supplier,
} from "../../api/warehouse";
import styles from "./WarehousePages.module.scss";

const emptyForm = {
  name: "",
  unp: "",
  phone: "",
  email: "",
  contactPerson: "",
  note: "",
};

export default function WarehouseSuppliersPage() {
  const { push } = useToast();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listSuppliers(search || undefined)
      .then((result) => setSuppliers(result.items))
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [search, push]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (supplier: Supplier) => {
    setEditing(supplier);
    setForm({
      name: supplier.name,
      unp: supplier.unp ?? "",
      phone: supplier.phone ?? "",
      email: supplier.email ?? "",
      contactPerson: supplier.contactPerson ?? "",
      note: supplier.note ?? "",
    });
    setModalOpen(true);
  };

  const submit = async () => {
    if (!form.name.trim()) {
      push("Укажите название поставщика", "error");
      return;
    }
      setSaving(true);
    try {
      if (editing) {
        await updateSupplier(editing.id, form);
        push("Поставщик обновлён", "success");
      } else {
        await createSupplier(form);
        push("Поставщик создан", "success");
      }
      setEditing(null);
      setModalOpen(false);
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (supplier: Supplier) => {
    try {
      await deactivateSupplier(supplier.id);
      push(`${supplier.name} деактивирован`, "info");
      load();
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const columns = [
    { key: "name", title: "Поставщик" },
    { key: "unp", title: "УНП" },
    { key: "contactPerson", title: "Контактное лицо" },
    { key: "phone", title: "Телефон" },
    {
      key: "isActive",
      title: "Статус",
      render: (row: Supplier) => (
        <Badge variant={row.isActive ? "success" : "neutral"}>
          {row.isActive ? "Активен" : "Не активен"}
        </Badge>
      ),
    },
    {
      key: "actions",
      title: "",
      render: (row: Supplier) => (
        <div style={{ display: "flex", gap: "0.4rem" }}>
          <Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
            Изменить
          </Button>
          {row.isActive && (
            <Button size="sm" variant="secondary" onClick={() => deactivate(row)}>
              Деактивировать
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Поставщики</h1>
        <Button
          onClick={() => {
            openCreate();
          }}
        >
          Добавить поставщика
        </Button>
      </div>
      <div className={styles.toolbar}>
        <Input
          label="Поиск"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Название или контактное лицо"
        />
      </div>
      <Table
        columns={columns}
        data={suppliers}
        rowKey={(row) => row.id}
        loading={loading}
        empty="Поставщиков нет"
      />

      <Modal
        open={modalOpen}
        onClose={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        title={editing ? `Поставщик: ${editing.name}` : "Новый поставщик"}
      >
        <div className={styles.formColumn}>
          <Input
            label="Название"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <Input
            label="УНП"
            value={form.unp}
            onChange={(event) => setForm({ ...form, unp: event.target.value })}
          />
          <Input
            label="Контактное лицо"
            value={form.contactPerson}
            onChange={(event) => setForm({ ...form, contactPerson: event.target.value })}
          />
          <Input
            label="Телефон"
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
          />
          <Input
            label="Email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
          <Button onClick={submit} loading={saving}>
            Сохранить
          </Button>
        </div>
      </Modal>
    </div>
  );
}
