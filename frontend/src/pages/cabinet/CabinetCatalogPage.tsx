import { useCallback, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Pagination, Select, Table, Textarea, useToast } from "../../components/ui";
import { listBrands, listCategories } from "../../api/catalog";
import { listCabinetProducts, type CabinetProduct } from "../../api/cabinetCatalog";
import { putCartItem as putServerCartItem } from "../../api/cart";
import { formatMoney } from "../../lib/format";
import { stockStatusBadge } from "../../lib/stockStatus";
import styles from "./CabinetCatalogPage.module.scss";

const PAGE_SIZE = 20;

function splitSkuList(value: string): string[] {
  return value
    .split(/[,;\n\r\t]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export default function CabinetCatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const categoryId = searchParams.get("categoryId") ?? "";
  const brandId = searchParams.get("brandId") ?? "";
  const search = searchParams.get("search") ?? "";
  const page = Math.max(0, Number(searchParams.get("page")) || 0);

  const [draftSearch, setDraftSearch] = useState(search);
  const [skuListDraft, setSkuListDraft] = useState("");
  const [skuListError, setSkuListError] = useState<string | null>(null);
  const [skuListSuccess, setSkuListSuccess] = useState<string | null>(null);
  const [rowQuantities, setRowQuantities] = useState<Record<string, number>>({});
  // plan-03: busy-стейт на время мутаций — повторный клик по «В корзину»/«Добавить список» невозможен
  const busyRowsRef = useRef(new Set<string>());
  const [busyRows, setBusyRows] = useState<ReadonlySet<string>>(new Set());
  const [bulkAdding, setBulkAdding] = useState(false);
  const queryClient = useQueryClient();
  const { push: pushToast } = useToast();

  const markRowBusy = useCallback((id: string, busy: boolean) => {
    if (busy) busyRowsRef.current.add(id);
    else busyRowsRef.current.delete(id);
    setBusyRows(new Set(busyRowsRef.current));
  }, []);

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  const { data: brands = [] } = useQuery({
    queryKey: ["brands"],
    queryFn: listBrands,
  });

  const { data: products, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ["cabinet-catalog", { page, size: PAGE_SIZE, search, categoryId, brandId }],
    queryFn: () => listCabinetProducts({ page, size: PAGE_SIZE, search, categoryId, brandId }),
  });

  // Preload all products for SKU bulk add (MVP: local lookup over full catalog)
  const { data: allProducts } = useQuery({
    queryKey: ["cabinet-catalog", "all"],
    queryFn: () => listCabinetProducts({ size: 1000 }),
    staleTime: 5 * 60 * 1000,
  });

  const allProductsBySku = useMemo(() => {
    const map = new Map<string, CabinetProduct>();
    allProducts?.items.forEach((product) => map.set(product.sku, product));
    return map;
  }, [allProducts]);

  function updateParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    next.set("page", "0");
    setSearchParams(next, { replace: true });
  }

  function handleRowQuantityChange(sku: string, value: string) {
    const quantity = Math.max(0, Number(value));
    setRowQuantities((prev) => ({ ...prev, [sku]: quantity }));
  }

  async function handleAddToCart(product: CabinetProduct) {
    if (busyRows.has(product.id)) return;
    const quantity = rowQuantities[product.sku] || 1;
    markRowBusy(product.id, true);
    try {
      await putServerCartItem(product.id, quantity);
      await queryClient.invalidateQueries({ queryKey: ["cart"] });
      setRowQuantities((prev) => ({ ...prev, [product.sku]: 0 }));
    } catch (err) {
      pushToast((err as Error).message, "error");
    } finally {
      markRowBusy(product.id, false);
    }
  }

  async function handleBulkAdd() {
    if (bulkAdding) return;
    setSkuListError(null);
    setSkuListSuccess(null);

    const skus = splitSkuList(skuListDraft);
    if (skus.length === 0) {
      setSkuListError("Вставьте хотя бы один артикул");
      return;
    }

    const notFound: string[] = [];
    let added = 0;
    let failed = 0;

    setBulkAdding(true);
    try {
      for (const sku of skus) {
        const product = allProductsBySku.get(sku);
        if (!product) {
          notFound.push(sku);
          continue;
        }
        try {
          await putServerCartItem(product.id, 1);
          added++;
        } catch {
          failed++;
        }
      }
      await queryClient.invalidateQueries({ queryKey: ["cart"] });
    } finally {
      setBulkAdding(false);
    }

    if (notFound.length > 0) {
      setSkuListError(`Не найдены: ${notFound.join(", ")}`);
    }
    if (failed > 0) {
      pushToast(`Не добавлено позиций: ${failed}`, "error");
    }
    if (added > 0) {
      setSkuListSuccess(`Добавлено позиций: ${added}`);
      setSkuListDraft("");
    }
  }

  const categoryOptions = [
    { value: "", label: "Все категории" },
    ...categories.map((c) => ({ value: c.id, label: c.name })),
  ];

  const brandOptions = [
    { value: "", label: "Все бренды" },
    ...brands.map((b) => ({ value: b.id, label: b.name })),
  ];

  const columns = [
    { key: "sku", title: "Артикул", width: "12%" },
    { key: "name", title: "Название", width: "35%" },
    { key: "unit", title: "Ед. изм.", width: "10%" },
    {
      key: "customerPrice",
      title: "Цена клиента",
      width: "14%",
      align: "right" as const,
      render: (row: CabinetProduct) => formatMoney(row.customerPrice),
    },
    {
      key: "availability",
      title: "Наличие",
      width: "12%",
      render: (row: CabinetProduct) => stockStatusBadge(row.stockStatus),
    },
    {
      key: "quantity",
      title: "Кол-во",
      width: "12%",
      align: "center" as const,
      render: (row: CabinetProduct) => (
        <Input
          type="number"
          min={0}
          step={row.weightStep ?? 1}
          value={rowQuantities[row.sku] ?? 1}
          onChange={(event) => handleRowQuantityChange(row.sku, event.target.value)}
          className={styles.quantityInput}
        />
      ),
    },
    {
      key: "actions",
      title: "",
      width: "15%",
      align: "center" as const,
      render: (row: CabinetProduct) => (
        <Button
          size="sm"
          loading={busyRows.has(row.id)}
          onClick={() => handleAddToCart(row)}
        >
          В корзину
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <section className={styles.bulk}>
          <h2 className={styles.bulk__title}>Массовое добавление по артикулам</h2>
          <p className={styles.bulk__hint}>
            Вставьте список артикулов через запятую, пробел или с новой строки
          </p>
          <div className={styles.bulk__row}>
            <Textarea
              placeholder="Например: bedro-kurinoye, steyk-ribay"
              value={skuListDraft}
              onChange={(event) => setSkuListDraft(event.target.value)}
              className={styles.bulk__textarea}
            />
            <Button onClick={handleBulkAdd} loading={bulkAdding} className={styles.bulk__button}>
              Добавить список
            </Button>
          </div>
          {skuListError && <p className={styles.bulk__error}>{skuListError}</p>}
          {skuListSuccess && <p className={styles.bulk__success}>{skuListSuccess}</p>}
        </section>

        <section className={styles.filters}>
          <Select
            label="Категория"
            options={categoryOptions}
            value={categoryId}
            onChange={(event) => updateParam("categoryId", event.target.value)}
          />
          <Select
            label="Бренд"
            options={brandOptions}
            value={brandId}
            onChange={(event) => updateParam("brandId", event.target.value)}
          />
          <Input
            label="Быстрый поиск"
            placeholder="Название товара"
            value={draftSearch}
            onChange={(event) => setDraftSearch(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                updateParam("search", draftSearch);
              }
            }}
          />
        </section>

        {isError ? (
          <p className={styles.loadError}>
            Не удалось загрузить каталог.{" "}
            <Button
              size="sm"
              variant="secondary"
              loading={isRefetching}
              onClick={() => refetch()}
            >
              Повторить
            </Button>
          </p>
        ) : (
          <Table
            columns={columns}
            data={products?.items ?? []}
            rowKey={(row) => row.id}
            loading={isLoading}
            empty="Товары не найдены. Попробуйте изменить фильтры."
          />
        )}

        {!isError && products && products.total > PAGE_SIZE && (
          <div className={styles.pagination}>
            <Pagination
              page={page + 1}
              size={PAGE_SIZE}
              total={products.total}
              onChange={(nextPage) => updateParam("page", String(nextPage - 1))}
            />
          </div>
        )}
      </main>
    </div>
  );
}
