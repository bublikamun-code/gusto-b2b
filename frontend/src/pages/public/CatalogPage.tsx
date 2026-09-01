import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Input, Pagination, Select } from "../../components/ui";
import { listBrands, listCategories, listProducts } from "../../api/catalog";
import { ProductCard } from "../../components/public/ProductCard";
import styles from "./CatalogPage.module.scss";

const PAGE_SIZE = 9;

export default function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const categoryId = searchParams.get("categoryId") ?? "";
  const brandId = searchParams.get("brandId") ?? "";
  const search = searchParams.get("search") ?? "";
  const page = Math.max(0, Number(searchParams.get("page") ?? "0"));

  const [draftSearch, setDraftSearch] = useState(search);

  useEffect(() => {
    setDraftSearch(search);
  }, [search]);

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  const { data: brands = [] } = useQuery({
    queryKey: ["brands"],
    queryFn: listBrands,
  });

  const { data: products, isLoading } = useQuery({
    queryKey: ["products", "catalog", { page, size: PAGE_SIZE, search, categoryId, brandId }],
    queryFn: () => listProducts({ page, size: PAGE_SIZE, search, categoryId, brandId }),
  });

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

  const categoryOptions = [
    { value: "", label: "Все категории" },
    ...categories.map((c) => ({ value: c.id, label: c.name })),
  ];

  const brandOptions = [
    { value: "", label: "Все бренды" },
    ...brands.map((b) => ({ value: b.id, label: b.name })),
  ];

  return (
    <section className={styles.catalog}>
      <header className={styles.catalog__header}>
        <span className={styles.catalog__eyebrow}>Всё свежее</span>
        <h1 className={styles.catalog__title}>Каталог</h1>
      </header>

      <div className={styles.catalog__filters}>
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
          label="Поиск"
          placeholder="Название товара"
          value={draftSearch}
          onChange={(event) => setDraftSearch(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              updateParam("search", draftSearch);
            }
          }}
        />
      </div>

      {isLoading && <p className={styles.catalog__status}>Загружаем витрину…</p>}

      {!isLoading && products?.items.length === 0 && (
        <p className={styles.catalog__status}>Товары не найдены. Попробуйте изменить фильтры.</p>
      )}

      <div className={styles.catalog__grid}>
        {products?.items.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>

      {products && products.total > PAGE_SIZE && (
        <div className={styles.catalog__pagination}>
          <Pagination
            page={page + 1}
            size={PAGE_SIZE}
            total={products.total}
            onChange={(nextPage) => updateParam("page", String(nextPage - 1))}
          />
        </div>
      )}
    </section>
  );
}
