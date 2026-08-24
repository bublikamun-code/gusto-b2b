import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  Input,
  Modal,
  Pagination,
  Select,
  Table,
  Tabs,
  Textarea,
  useToast,
  type Column,
} from "../components/ui";
import styles from "./UiKitPage.module.scss";

interface DemoRow {
  sku: string;
  name: string;
  unit: string;
  price: string;
  stock: string;
}

const demoRows: DemoRow[] = [
  { sku: "A-100", name: "Филе куриное охлаждённое", unit: "кг", price: "8,90", stock: "120" },
  { sku: "A-101", name: "Бедро куриное без кости", unit: "кг", price: "7,40", stock: "85" },
  { sku: "B-205", name: "Вырезка свиная", unit: "кг", price: "16,20", stock: "24" },
  { sku: "B-206", name: "Фарш говяжий 80/20", unit: "кг", price: "12,50", stock: "60" },
  { sku: "C-310", name: "Яйцо куриное С1, 10 шт", unit: "уп", price: "3,80", stock: "200" },
];

const demoColumns: Column<DemoRow>[] = [
  { key: "sku", title: "Артикул" },
  { key: "name", title: "Наименование" },
  { key: "unit", title: "Ед. изм.", align: "center", width: "6rem" },
  {
    key: "price",
    title: "Цена",
    align: "right",
    width: "7rem",
    render: (row) => `${row.price} р./${row.unit}`,
  },
  {
    key: "stock",
    title: "Статус",
    width: "9rem",
    render: (row) =>
      Number(row.stock) > 30 ? <Badge variant="success">В наличии</Badge> : <Badge variant="warning">Под заказ</Badge>,
  },
];

export default function UiKitPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("all");
  const [page, setPage] = useState(3);
  const toast = useToast();

  return (
    <div className="page">
      <header className="header">
        <span className="logo">ГУСТО</span>
        <span className="header__sub">дизайн-система · ui-kit</span>
      </header>

      <main className={styles.main}>
        <h1 className={styles.pageTitle}>UI-Kit · S06</h1>
        <p className={styles.pageNote}>
          Все компоненты в состояниях по бренд-буку (docs/brandbook). Приёмка S06 — визуальная
          сверка этой страницы с бренд-буком.
        </p>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Button</h2>
          <div className={styles.row}>
            <Button>Основная</Button>
            <Button variant="secondary">Вторичная</Button>
            <Button variant="accent">В корзину</Button>
            <Button disabled>Недоступна</Button>
            <Button loading>Сохранение…</Button>
          </div>
          <div className={styles.row}>
            <Button size="sm">Small</Button>
            <Button size="md">Medium</Button>
            <Button size="lg">Large</Button>
          </div>
          <div className={styles.row}>
            <Button block>На всю ширину</Button>
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Input · Textarea · Select</h2>
          <div className={styles.grid}>
            <Input label="Email" placeholder="zakaz@company.by" type="email" />
            <Input label="Телефон" placeholder="+375 (29) 000-00-00" />
            <Input label="С ошибкой" defaultValue="abc" error="Введите корректный email" />
            <Input label="Недоступное" disabled placeholder="Недоступно" />
            <Select
              label="Категория"
              placeholder="Выберите категорию"
              options={[
                { value: "meat", label: "Мясо" },
                { value: "poultry", label: "Птица" },
                { value: "eggs", label: "Яйца" },
              ]}
            />
            <Select label="Недоступный" disabled options={[{ value: "x", label: "X" }]} />
            <Textarea label="Комментарий к заказу" placeholder="Время доставки, подъезд…" />
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Badge</h2>
          <div className={styles.row}>
            <Badge variant="success">В наличии</Badge>
            <Badge variant="warning">Под заказ</Badge>
            <Badge variant="accent">ХИТ</Badge>
            <Badge variant="accent">НОВИНКА</Badge>
            <Badge variant="neutral">Черновик</Badge>
            <Badge variant="outline">-7%</Badge>
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Card</h2>
          <div className={styles.grid}>
            <Card title="Карточка с заголовком">
              <p>Тело карточки: фон поверх сливок, без теней и градиентов.</p>
            </Card>
            <Card accent title="С бордовой полосой">
              <p>Акцентная карточка — верхняя планка бордо.</p>
            </Card>
            <Card
              title="С действием"
              actions={<Button size="sm" variant="secondary">Открыть</Button>}
            >
              <p>В заголовке можно разместить действия.</p>
            </Card>
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Tabs</h2>
          <Tabs
            active={activeTab}
            onChange={setActiveTab}
            items={[
              { key: "all", label: "Все" },
              { key: "meat", label: "Мясо" },
              { key: "poultry", label: "Птица" },
              { key: "eggs", label: "Яйца" },
            ]}
          />
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Table</h2>
          <Table columns={demoColumns} data={demoRows} rowKey={(row) => row.sku} />
          <h3 className={styles.subTitle}>Пустое состояние</h3>
          <Table columns={demoColumns} data={[]} rowKey={(row) => row.sku} empty="Товары не найдены" />
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Pagination</h2>
          <Pagination page={page} size={20} total={137} onChange={setPage} />
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Modal · Toast</h2>
          <div className={styles.row}>
            <Button onClick={() => setModalOpen(true)}>Открыть модальное окно</Button>
            <Button variant="secondary" onClick={() => toast.push("Заказ создан", "success")}>
              Toast: успех
            </Button>
            <Button variant="secondary" onClick={() => toast.push("Недостаточно товара на складе", "error")}>
              Toast: ошибка
            </Button>
            <Button variant="secondary" onClick={() => toast.push("Прайс обновлён", "info")}>
              Toast: инфо
            </Button>
          </div>
        </section>
      </main>

      <footer className="footer">gustomeat.by · B2B-портал</footer>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Подтверждение заказа"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>
              Отмена
            </Button>
            <Button
              onClick={() => {
                setModalOpen(false);
                toast.push("Заказ подтверждён", "success");
              }}
            >
              Подтвердить
            </Button>
          </>
        }
      >
        <p>
          Создать заказ на 5 позиций, сумма 1 489,00 р. с НДС 10%? Доставка: г. Минск,
          ул. Монтажников, 39.
        </p>
      </Modal>
    </div>
  );
}
