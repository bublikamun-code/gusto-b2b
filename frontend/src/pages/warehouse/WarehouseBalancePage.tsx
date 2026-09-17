import { useCallback, useEffect, useState } from "react";
import { Badge, Input, Select, Table } from "../../components/ui";
import {
  getBalanceReport,
  listLocations,
  type BalanceRow,
  type StockLocation,
} from "../../api/warehouse";
import styles from "./WarehousePages.module.scss";

export default function WarehouseBalancePage() {
  const [rows, setRows] = useState<BalanceRow[]>([]);
  const [locations, setLocations] = useState<StockLocation[]>([]);
  const [locationId, setLocationId] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listLocations()
      .then(setLocations)
      .catch((err) => setError((err as Error).message));
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getBalanceReport(locationId || undefined, search || undefined)
      .then(setRows)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [locationId, search]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = [
    { key: "sku", title: "Артикул" },
    { key: "productName", title: "Товар" },
    { key: "locationName", title: "Склад" },
    { key: "quantity", title: "Всего", align: "right" as const },
    { key: "reserved", title: "Резерв", align: "right" as const },
    {
      key: "available",
      title: "Доступно",
      align: "right" as const,
      render: (row: BalanceRow) => {
        const deficit = row.minStock > 0 && row.available < row.minStock;
        return (
          <span className={deficit ? styles.deficit : undefined}>
            {row.available}
            {deficit && (
              <>
                {" "}
                <Badge variant="accent">ниже минимума {row.minStock}</Badge>
              </>
            )}
          </span>
        );
      },
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Остатки по складам</h1>
      </div>
      {error && <p role="alert">Ошибка: {error}</p>}
      <div className={styles.toolbar}>
        <Select
          label="Склад"
          value={locationId}
          onChange={(event) => setLocationId(event.target.value)}
          options={[
            { value: "", label: "Все склады" },
            ...locations.map((location) => ({ value: location.id, label: location.name })),
          ]}
        />
        <Input
          label="Поиск товара"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Артикул или название"
        />
      </div>
      <Table
        columns={columns}
        data={rows}
        rowKey={(row) => `${row.productId}-${row.locationId}`}
        loading={loading}
        empty="Остатков нет"
      />
      <p>Единица хранения — кг/десяток; суммы резервов учитываются в «Доступно».</p>
    </div>
  );
}
