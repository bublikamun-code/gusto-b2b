import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Input, Select, Textarea, useToast } from "../../components/ui";
import { createOrder } from "../../api/cart";
import { listManagerCompanies } from "../../api/orders";
import { listCabinetProducts, type CabinetProduct } from "../../api/cabinetCatalog";
import { formatMoney } from "../../lib/format";
import styles from "./ManagerOrderCreatePage.module.scss";

interface LineDraft {
  quantity: string;
}

export default function ManagerOrderCreatePage() {
  const { push } = useToast();
  const navigate = useNavigate();

  const [companies, setCompanies] = useState<Array<{ id: string; name: string; unp?: string | null }>>([]);
  const [companyId, setCompanyId] = useState("");
  const [products, setProducts] = useState<CabinetProduct[]>([]);
  const [search, setSearch] = useState("");
  const [lines, setLines] = useState<Record<string, LineDraft>>({});
  const [deliveryType, setDeliveryType] = useState<"PICKUP" | "DELIVERY">("PICKUP");
  const [address, setAddress] = useState("");
  const [recipientName, setRecipientName] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    listManagerCompanies()
      .then((page) => setCompanies(page.items))
      .catch((err) => push((err as Error).message, "error"));
  }, [push]);

  useEffect(() => {
    listCabinetProducts({ page: 0, size: 30, search: search || undefined })
      .then((page) => setProducts(page.items))
      .catch((err) => push((err as Error).message, "error"));
    // push стабилен (useToast), поиск — осознанный триггер без debounce для MVP
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const selectedItems = useMemo(
    () =>
      Object.entries(lines)
        .map(([productId, draft]) => ({ productId, quantity: Number(draft.quantity.replace(",", ".")) }))
        .filter((line) => line.quantity > 0),
    [lines],
  );

  const submit = async () => {
    if (!companyId) {
      push("Выберите компанию клиента", "error");
      return;
    }
    if (selectedItems.length === 0) {
      push("Добавьте хотя бы одну позицию", "error");
      return;
    }
    if (deliveryType === "DELIVERY" && (!address.trim() || !recipientName.trim() || !recipientPhone.trim())) {
      push("Для доставки нужны адрес, имя и телефон получателя", "error");
      return;
    }
    setSubmitting(true);
    try {
      const order = await createOrder(
        {
          customerCompanyId: companyId,
          items: selectedItems,
          deliveryType,
          deliveryAddress: deliveryType === "DELIVERY" ? address : undefined,
          recipientName: recipientName || undefined,
          recipientPhone: recipientPhone || undefined,
          note: note || undefined,
        },
        crypto.randomUUID(),
      );
      push(`Заказ ${order.number} создан`, "success");
      navigate("/manager/orders");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Заказ от имени клиента</h1>
        <Link to="/manager/orders">К списку заказов</Link>
      </header>

      <Card className={styles.card}>
        <h2>Клиент</h2>
        <Select
          value={companyId}
          placeholder="Выберите компанию…"
          options={companies.map((c) => ({
            value: c.id,
            label: c.unp ? `${c.name} (УНП ${c.unp})` : c.name,
          }))}
          onChange={(e) => setCompanyId(e.target.value)}
        />
      </Card>

      <Card className={styles.card}>
        <h2>Позиции</h2>
        <Input
          label="Поиск товара"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Начните вводить название"
        />
        <table className={styles.products}>
          <thead>
            <tr>
              <th>Артикул</th>
              <th>Название</th>
              <th>Цена</th>
              <th className={styles.qtyCol}>Кол-во</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>{product.sku}</td>
                <td>{product.name}</td>
                <td>{formatMoney(Number(product.customerPrice ?? 0))}</td>
                <td>
                  <Input
                    type="number"
                    min="0"
                    step="0.5"
                    value={lines[product.id]?.quantity ?? ""}
                    onChange={(e) =>
                      setLines((prev) => ({
                        ...prev,
                        [product.id]: { quantity: e.target.value },
                      }))
                    }
                  />
                </td>
              </tr>
            ))}
            {products.length === 0 && (
              <tr>
                <td colSpan={4}>Ничего не найдено</td>
              </tr>
            )}
          </tbody>
        </table>
        <p className={styles.hint}>Итоговая сумма рассчитается по ценам этого клиента при создании заказа.</p>
      </Card>

      <Card className={styles.card}>
        <h2>Доставка</h2>
        <Select
          value={deliveryType}
          options={[
            { value: "PICKUP", label: "Самовывоз" },
            { value: "DELIVERY", label: "Доставка" },
          ]}
          onChange={(e) => setDeliveryType(e.target.value as "PICKUP" | "DELIVERY")}
        />
        {deliveryType === "DELIVERY" && (
          <>
            <Input label="Адрес доставки" value={address} onChange={(e) => setAddress(e.target.value)} />
            <Input
              label="Имя получателя"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
            />
            <Input
              label="Телефон получателя"
              value={recipientPhone}
              onChange={(e) => setRecipientPhone(e.target.value)}
            />
          </>
        )}
        <Textarea label="Комментарий" value={note} onChange={(e) => setNote(e.target.value)} />
        <div className={styles.actions}>
          <Button variant="accent" loading={submitting} disabled={selectedItems.length === 0} onClick={submit}>
            Создать заказ ({selectedItems.length} поз.)
          </Button>
        </div>
      </Card>
    </div>
  );
}
