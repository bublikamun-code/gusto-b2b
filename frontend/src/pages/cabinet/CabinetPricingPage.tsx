import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Card,
  Input,
  Pagination,
  Select,
  Table,
  Tabs,
  useToast,
} from "../../components/ui";
import type { Column } from "../../components/ui";
import {
  downloadPricingXlsx,
  listCabinetProducts,
  listClientDiscounts,
  type CabinetProduct,
  type ClientDiscount,
} from "../../api/cabinetCatalog";
import { listCategories, listBrands } from "../../api/catalog";
import { formatMoney } from "../../lib/format";
import styles from "./CabinetPricingPage.module.scss";

type Category = { id: string; name: string };
type Brand = { id: string; name: string };

/**
 * «Прайс и скидки» — экран, требуемый матрицей 2.1 для клиента-юрлица
 * («Прайс/скидки в кабинете»: ADMIN +, MANAGER +, юрлицо + свои, физлицо −).
 *
 * <p>Раньше в кабинете не было никакого прайса: был только экспорт .xlsx для 1С,
 * доступный с админского экрана, и клиент со своего кабинета не мог посмотреть,
 * сколько он платит и какие на него действуют скидки.
 */
export default function CabinetPricingPage() {
  
  const push = useToast();
  const [tab, setTab] = useState("prices");
  const [search, setSearch] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [brandId, setBrandId] = useState("");
  const [page, setPage] = useState(1);
  const [exporting, setExporting] = useState(false);

  const products = useQuery({
    queryKey: ["cabinet", "pricing", search, categoryId, brandId, page],
    queryFn: () =>
      listCabinetProducts({
        page: page - 1,
        size: 50,
        search: search || undefined,
        categoryId: categoryId || undefined,
        brandId: brandId || undefined,
      }),
  });

  const discounts = useQuery({
    queryKey: ["cabinet", "discounts"],
    queryFn: listClientDiscounts,
  });

  const categories = useQuery({ queryKey: ["catalog", "categories"], queryFn: listCategories });
  const brands = useQuery({ queryKey: ["catalog", "brands"], queryFn: listBrands });

  const categoryOptions = useMemo(
    () => [{ value: "", label: "Все категории" }, ...(categories.data ?? []).map((c: Category) => ({ value: c.id, label: c.name }))],
    [categories.data],
  );
  const brandOptions = useMemo(
    () => [{ value: "", label: "Все бренды" }, ...(brands.data ?? []).map((b: Brand) => ({ value: b.id, label: b.name }))],
    [brands.data],
  );

  const priceColumns: Column<CabinetProduct>[] = [
    { key: "sku", title: "Артикул" },
    { key: "name", title: "Наименование" },
    {
      key: "retailPrice",
      title: "Розница",
      align: "right",
      render: (p) => <span className={styles.retail}>{formatMoney(Number(p.retailPrice ?? 0))}</span>,
    },
    {
      key: "customerPrice",
      title: "Ваша цена",
      align: "right",
      render: (p) => <strong>{formatMoney(Number(p.customerPrice ?? 0))}</strong>,
    },
    {
      key: "discount",
      title: "Скидка",
      align: "right",
      render: (p) => {
        const retail = Number(p.retailPrice ?? 0);
        const mine = Number(p.customerPrice ?? 0);
        if (!retail || retail <= mine) return <span className={styles.noDiscount}>—</span>;
        const percent = Math.round((1 - mine / retail) * 100);
        return <Badge variant="success">−{percent}%</Badge>;
      },
    },
    { key: "unit", title: "Ед." },
  ];

  const discountColumns: Column<ClientDiscount>[] = [
    {
      key: "scope",
      title: "Действует на",
      render: (d) => {
        const parts: string[] = [];
        if (d.brandName) parts.push(`бренд «${d.brandName}»`);
        if (d.categoryName) parts.push(`категория «${d.categoryName}»`);
        return parts.length > 0 ? parts.join(" + ") : "весь каталог";
      },
    },
    {
      key: "percent",
      title: "Скидка",
      align: "right",
      render: (d) => <strong>{formatPercent(d.discountPercent)}</strong>,
    },
    {
      key: "period",
      title: "Период",
      render: (d) => `${formatDate(d.validFrom)} — ${d.validTo ? formatDate(d.validTo) : "бессрочно"}`,
    },
    {
      key: "active",
      title: "Статус",
      align: "right",
      render: (d) =>
        d.active ? <Badge variant="success">действует</Badge> : <Badge variant="neutral">истекла</Badge>,
    },
  ];

  const handleExport = async () => {
    setExporting(true);
    try {
      await downloadPricingXlsx();
      push.push("Прайс выгружен", "success");
    } catch (err) {
      push.push((err as Error).message, "error");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="page">
      <header className={styles.pageHeader}>
        <div>
          <h1 className={styles.title}>Прайс и скидки</h1>
          <p className={styles.subtitle}>
            Ваши цены с учётом индивидуальных условий и действующих скидок.
          </p>
        </div>
        <Button variant="secondary" loading={exporting} onClick={handleExport}>
          Выгрузить в Excel
        </Button>
      </header>

      <Card>
        <Tabs
          items={[
            { key: "prices", label: "Прайс" },
            { key: "discounts", label: `Скидки${discounts.data?.length ? ` (${discounts.data.length})` : ""}` },
          ]}
          active={tab}
          onChange={(next) => {
            setTab(next);
            setPage(1);
          }}
        />

        {tab === "prices" && (
          <>
            <div className={styles.filters}>
              <Input
                placeholder="Поиск по названию"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
              />
              <Select
                value={categoryId}
                onChange={(e) => {
                  setCategoryId(e.target.value);
                  setPage(1);
                }}
                options={categoryOptions}
                aria-label="Категория"
              />
              <Select
                value={brandId}
                onChange={(e) => {
                  setBrandId(e.target.value);
                  setPage(1);
                }}
                options={brandOptions}
                aria-label="Бренд"
              />
            </div>

            <Table
              columns={priceColumns}
              data={products.data?.items ?? []}
              rowKey={(p) => p.id}
              loading={products.isLoading}
              empty="Товаров не найдено"
            />

            <div className={styles.pagination}>
              <Pagination
                page={page}
                size={50}
                total={products.data?.total ?? 0}
                onChange={setPage}
              />
            </div>
          </>
        )}

        {tab === "discounts" && (
          <Table
            columns={discountColumns}
            data={discounts.data ?? []}
            rowKey={(d) => d.id}
            loading={discounts.isLoading}
            empty="Индивидуальных скидок не назначено"
          />
        )}
      </Card>
    </div>
  );
}

function formatPercent(value: number): string {
  return `${Number(value).toFixed(0).replace(".", ",")}%`;
}

function formatDate(iso: string): string {
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day}.${month}.${year}`;
}