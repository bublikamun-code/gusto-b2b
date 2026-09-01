import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Pagination, Select, Table, Textarea } from "../../components/ui";
import { listBrands, listCategories } from "../../api/catalog";
import { listCabinetProducts, type CabinetProduct } from "../../api/cabinetCatalog";
import { useCartStore, selectCartTotalCount, selectCartTotalSum } from "../../store/cartStore";
import { useAuthStore } from "../../store/authStore";
import { logout } from "../../api/auth";
import styles from "./CabinetCatalogPage.module.scss";

const PAGE_SIZE = 20;

function formatMoney(value: number): string {
  return new Intl.NumberFormat("ru-BY", {
    style: "currency",
    currency: "BYN",
    minimumFractionDigits: 2,
  }).format(value);
}

function splitSkuList(value: string): string[] {
  return value
    .split(/[,;\n\r\t]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export default function CabinetCatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const categoryId = searchParams.get("categoryId") ?? "";
  const brandId = searchParams.get("brandId") ?? "";
  const search = searchParams.get("search") ?? "";
  const page = Math.max(0, Number(searchParams.get("page") ?? "0"));

  const [draftSearch, setDraftSearch] = useState(search);
  const [skuListDraft, setSkuListDraft] = useState("");
  const [skuListError, setSkuListError] = useState<string | null>(null);
  const [skuListSuccess, setSkuListSuccess] = useState<string | null>(null);
  const [rowQuantities, setRowQuantities] = useState<Record<string, number>>({});

  const cartItems = useCartStore((s) => s.items);
  const addItem = useCartStore((s) => s.addItem);
  const cartCount = useMemo(() => selectCartTotalCount(cartItems), [cartItems]);
  const cartSum = useMemo(() => selectCartTotalSum(cartItems), [cartItems]);

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  const { data: brands = [] } = useQuery({
    queryKey: ["brands"],
    queryFn: listBrands,
  });

  const { data: products, isLoading } = useQuery({
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

  function handleLogout() {
    logout().finally(() => {
      clearAuth();
      window.location.href = "/login";
    });
  }

  function handleRowQuantityChange(sku: string, value: string) {
    const quantity = Math.max(0, Number(value));
    setRowQuantities((prev) => ({ ...prev, [sku]: quantity }));
  }

  function handleAddToCart(product: CabinetProduct) {
    const quantity = rowQuantities[product.sku] || 1;
    addItem(
      {
        productId: product.id,
        sku: product.sku,
        name: product.name,
        unit: product.unit,
        price: product.customerPrice,
      },
      quantity,
    );
    setRowQuantities((prev) => ({ ...prev, [product.sku]: 0 }));
  }

  function handleBulkAdd() {
    setSkuListError(null);
    setSkuListSuccess(null);

    const skus = splitSkuList(skuListDraft);
    if (skus.length === 0) {
      setSkuListError("Вставьте хотя бы один артикул");
      return;
    }

    const notFound: string[] = [];
    let added = 0;

    skus.forEach((sku) => {
      const product = allProductsBySku.get(sku);
      if (!product) {
        notFound.push(sku);
        return;
      }
      addItem(
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          unit: product.unit,
          price: product.customerPrice,
        },
        1,
      );
      added++;
    });

    if (notFound.length > 0) {
      setSkuListError(`Не найдены: ${notFound.join(", ")}`);
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
      render: () => "В наличии",
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
          step={1}
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
        <Button size="sm" onClick={() => handleAddToCart(row)}>
          В корзину
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.header__left}>
          <Link to="/cabinet" className={styles.header__logo}>
            ГУСТО
          </Link>
          <span className={styles.header__sub}>Каталог</span>
        </div>
        <div className={styles.header__right}>
          <div className={styles.header__cart}>
            <span className={styles.header__cartLabel}>Корзина</span>
            <span className={styles.header__cartCount}>{cartCount}</span>
            <span className={styles.header__cartSum}>{formatMoney(cartSum)}</span>
          </div>
          <span className={styles.header__user}>{user?.fullName ?? user?.email}</span>
          <Link to="/cabinet" className={styles.header__link}>
            Кабинет
          </Link>
          <Button size="sm" variant="secondary" onClick={handleLogout}>
            Выйти
          </Button>
        </div>
      </header>

      <main className={styles.main}>
        <section className={styles.bulk}>
          <h2 className={styles.bulk__title}>Массовое добавление по артикулам</h2>
          <p className={styles.bulk__hint}>
            Вставьте список артикулов через запятую, пробел или с новой строки
          </p>
          <div className={styles.bulk__row}>
            <Textarea
              placeholder="Например: KOLO-001, SOS-025"
              value={skuListDraft}
              onChange={(event) => setSkuListDraft(event.target.value)}
              className={styles.bulk__textarea}
            />
            <Button onClick={handleBulkAdd} className={styles.bulk__button}>
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

        <Table
          columns={columns}
          data={products?.items ?? []}
          rowKey={(row) => row.id}
          loading={isLoading}
          empty="Товары не найдены. Попробуйте изменить фильтры."
        />

        {products && products.total > PAGE_SIZE && (
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
