import { useCallback, useEffect, useState } from "react";
import {
  Badge,
  Button,
  Input,
  Modal,
  Pagination,
  Select,
  Table,
  useToast,
} from "../../components/ui";
import {
  cancelWarehouseDocument,
  confirmWarehouseDocument,
  createWarehouseDocument,
  listLocations,
  listProductRefs,
  listWarehouseDocuments,
  type CatalogProductRef,
  type StockLocation,
  type WarehouseDocument,
  type WarehouseDocumentType,
} from "../../api/warehouse";
import {
  DOCUMENT_STATUS_LABELS,
  DOCUMENT_TYPE_LABELS,
  DOCUMENT_TYPE_OPTIONS,
  locationFieldsForType,
  validateDocumentLocations,
} from "../../lib/warehouse";
import styles from "./WarehousePages.module.scss";

interface DraftItem {
  productId: string;
  quantity: string;
  price: string;
}

const emptyDraft = (type: WarehouseDocumentType) => ({
  type,
  locationFromId: "",
  locationToId: "",
  note: "",
  items: [{ productId: "", quantity: "1", price: "" }] as DraftItem[],
});

export default function WarehouseDocumentsPage() {
  const { push } = useToast();
  const [documents, setDocuments] = useState<WarehouseDocument[]>([]);
  const [locations, setLocations] = useState<StockLocation[]>([]);
  const [products, setProducts] = useState<CatalogProductRef[]>([]);
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState(emptyDraft("INCOMING"));
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listWarehouseDocuments({ type: typeFilter, status: statusFilter, page })
      .then((result) => {
        setDocuments(result.items);
        setTotal(result.total);
      })
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [typeFilter, statusFilter, page, push]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    listLocations().then(setLocations).catch(() => undefined);
    listProductRefs().then((result) => setProducts(result.items)).catch(() => undefined);
  }, []);

  const locationOptions = [
    { value: "", label: "— выберите склад —" },
    ...locations.map((location) => ({ value: location.id, label: location.name })),
  ];
  const productOptions = [
    { value: "", label: "— товар —" },
    ...products.map((product) => ({ value: product.id, label: `${product.sku} · ${product.name}` })),
  ];

  const fields = locationFieldsForType(draft.type);
  const locationError = validateDocumentLocations(draft.type, {
    locationFromId: draft.locationFromId,
    locationToId: draft.locationToId,
  });

  const submit = async () => {
    if (locationError) {
      push(locationError, "error");
      return;
    }
    if (draft.items.some((item) => !item.productId || Number(item.quantity) <= 0)) {
      push("Заполните позиции: товар и положительное количество", "error");
      return;
    }
    setSaving(true);
    try {
      await createWarehouseDocument({
        type: draft.type,
        locationFromId: draft.locationFromId || undefined,
        locationToId: draft.locationToId || undefined,
        note: draft.note || undefined,
        items: draft.items.map((item) => ({
          productId: item.productId,
          quantity: Number(item.quantity),
          price: item.price ? Number(item.price) : undefined,
        })),
      });
      push("Документ создан. Подтвердите его, чтобы провести движения.", "success");
      setCreating(false);
      setDraft(emptyDraft("INCOMING"));
      load();
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSaving(false);
    }
  };

  const act = async (action: "confirm" | "cancel", document: WarehouseDocument) => {
    try {
      if (action === "confirm") {
        await confirmWarehouseDocument(document.id);
        push(`${document.number} проведён`, "success");
      } else {
        await cancelWarehouseDocument(document.id);
        push(`${document.number} отменён`, "info");
      }
      load();
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const columns = [
    { key: "number", title: "Номер", render: (row: WarehouseDocument) => <span className={styles.number}>{row.number}</span> },
    { key: "type", title: "Тип", render: (row: WarehouseDocument) => DOCUMENT_TYPE_LABELS[row.type] },
    {
      key: "status",
      title: "Статус",
      render: (row: WarehouseDocument) => (
        <Badge
          variant={
            row.status === "CONFIRMED" ? "success" : row.status === "CANCELLED" ? "neutral" : "warning"
          }
        >
          {DOCUMENT_STATUS_LABELS[row.status]}
        </Badge>
      ),
    },
    { key: "documentDate", title: "Дата" },
    {
      key: "items",
      title: "Позиции",
      render: (row: WarehouseDocument) =>
        row.items
          .map((item) => `${item.sku ?? item.productId} × ${item.quantity}`)
          .join(", "),
    },
    {
      key: "actions",
      title: "",
      render: (row: WarehouseDocument) =>
        row.status === "DRAFT" ? (
          <div style={{ display: "flex", gap: "0.4rem" }}>
            <Button size="sm" onClick={() => act("confirm", row)}>
              Провести
            </Button>
            <Button size="sm" variant="secondary" onClick={() => act("cancel", row)}>
              Отменить
            </Button>
          </div>
        ) : null,
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Складские документы</h1>
        <Button onClick={() => setCreating(true)}>Создать документ</Button>
      </div>
      <div className={styles.toolbar}>
        <Select
          label="Тип"
          value={typeFilter}
          onChange={(event) => {
            setTypeFilter(event.target.value);
            setPage(0);
          }}
          options={[{ value: "", label: "Все типы" }, ...DOCUMENT_TYPE_OPTIONS]}
        />
        <Select
          label="Статус"
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          options={[
            { value: "", label: "Все статусы" },
            ...Object.entries(DOCUMENT_STATUS_LABELS).map(([value, label]) => ({ value, label })),
          ]}
        />
      </div>
      <Table
        columns={columns}
        data={documents}
        rowKey={(row) => row.id}
        loading={loading}
        empty="Документов нет"
      />
      <Pagination page={page + 1} size={20} total={total} onChange={(next) => setPage(next - 1)} />

      <Modal open={creating} onClose={() => setCreating(false)} title="Новый складской документ">
        <div className={styles.formColumn}>
          <Select
            label="Тип документа"
            value={draft.type}
            onChange={(event) =>
              setDraft({ ...draft, type: event.target.value as WarehouseDocumentType })
            }
            options={DOCUMENT_TYPE_OPTIONS}
          />
          {fields.from && (
            <Select
              label={fields.fromLabel}
              value={draft.locationFromId}
              onChange={(event) => setDraft({ ...draft, locationFromId: event.target.value })}
              options={locationOptions}
            />
          )}
          {fields.to && (
            <Select
              label="На склад"
              value={draft.locationToId}
              onChange={(event) => setDraft({ ...draft, locationToId: event.target.value })}
              options={locationOptions}
            />
          )}
          <Input
            label="Комментарий"
            value={draft.note}
            onChange={(event) => setDraft({ ...draft, note: event.target.value })}
          />

          <div className={styles.itemsEditor}>
            <strong>Позиции{draft.type === "INVENTORY" ? " (фактическое количество)" : ""}</strong>
            {draft.items.map((item, index) => (
              <div key={index} className={styles.itemRow}>
                <Select
                  label="Товар"
                  value={item.productId}
                  onChange={(event) => {
                    const items = [...draft.items];
                    items[index] = { ...item, productId: event.target.value };
                    setDraft({ ...draft, items });
                  }}
                  options={productOptions}
                />
                <Input
                  label="Кол-во"
                  type="number"
                  step="0.001"
                  min="0"
                  value={item.quantity}
                  onChange={(event) => {
                    const items = [...draft.items];
                    items[index] = { ...item, quantity: event.target.value };
                    setDraft({ ...draft, items });
                  }}
                />
                {draft.type === "INCOMING" && (
                  <Input
                    label="Цена"
                    type="number"
                    step="0.01"
                    min="0"
                    value={item.price}
                    onChange={(event) => {
                      const items = [...draft.items];
                      items[index] = { ...item, price: event.target.value };
                      setDraft({ ...draft, items });
                    }}
                  />
                )}
                <Button
                  variant="secondary"
                  onClick={() =>
                    setDraft({ ...draft, items: draft.items.filter((_, i) => i !== index) })
                  }
                >
                  ✕
                </Button>
              </div>
            ))}
            <div>
              <Button
                variant="secondary"
                onClick={() =>
                  setDraft({
                    ...draft,
                    items: [...draft.items, { productId: "", quantity: "1", price: "" }],
                  })
                }
              >
                + позиция
              </Button>
            </div>
          </div>

          <Button onClick={submit} loading={saving}>
            Создать документ
          </Button>
        </div>
      </Modal>
    </div>
  );
}
