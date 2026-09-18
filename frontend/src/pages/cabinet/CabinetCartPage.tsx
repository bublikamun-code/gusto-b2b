import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Input, Select, Table, useToast } from "../../components/ui";
import {
  clearCart,
  createOrder,
  getCart,
  putCartItem,
  type CartLine,
  type ServerCart,
} from "../../api/cart";
import { useAuthStore } from "../../store/authStore";
import { formatMoney } from "../../lib/format";
import styles from "./CabinetCartPage.module.scss";

export default function CabinetCartPage() {
  const { push } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const [cart, setCart] = useState<ServerCart | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState<{ number: string } | null>(null);

  const [deliveryType, setDeliveryType] = useState<"PICKUP" | "DELIVERY">("PICKUP");
  const [address, setAddress] = useState("");
  const [recipientName, setRecipientName] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(() => {
    setLoading(true);
    getCart()
      .then(setCart)
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const isRetail = user?.role === "CUSTOMER_INDIVIDUAL";
  const isEmpty = !cart || cart.items.length === 0;

  const changeQuantity = async (productId: string, quantity: number) => {
    try {
      setCart(await putCartItem(productId, quantity));
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const submit = async () => {
    if (deliveryType === "DELIVERY" && !address.trim()) {
      push("Укажите адрес доставки", "error");
      return;
    }
    if (!recipientName.trim() || !recipientPhone.trim()) {
      push("Укажите имя и телефон получателя", "error");
      return;
    }
    setSubmitting(true);
    try {
      const order = await createOrder(
        {
          deliveryType,
          deliveryAddress: deliveryType === "DELIVERY" ? address : undefined,
          recipientName,
          recipientPhone,
          note: note || undefined,
        },
        crypto.randomUUID(),
      );
      setDone({ number: order.number });
      queryClient.invalidateQueries({ queryKey: ["cart"] });
      push(`Заказ ${order.number} создан`, "success");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Заказ {done.number} принят</h1>
        <p>Менеджер свяжется с вами для подтверждения. Заказ появился в истории заказов.</p>
        <div className={styles.actions}>
          <Button onClick={() => navigate("/cabinet/cart", { replace: true })}>К корзине</Button>
        </div>
      </div>
    );
  }

  const columns = [
    { key: "sku", title: "Артикул" },
    { key: "productName", title: "Товар" },
    { key: "unit", title: "Ед." },
    {
      key: "quantity",
      title: "Кол-во",
      align: "center" as const,
      render: (row: ServerCart["items"][number]) => (
        <Input
          type="number"
          min={0}
          step={0.001}
          value={row.quantity}
          onChange={(event) => changeQuantity(row.productId, Number(event.target.value))}
          className={styles.quantityInput}
        />
      ),
    },
    { key: "unitPrice", title: "Цена", align: "right" as const, render: (row: CartLine) => formatMoney(row.unitPrice) },
    { key: "total", title: "Сумма", align: "right" as const, render: (row: CartLine) => formatMoney(row.total) },
    {
      key: "actions",
      title: "",
      render: (row: ServerCart["items"][number]) => (
        <Button size="sm" variant="secondary" onClick={() => changeQuantity(row.productId, 0)}>
          Убрать
        </Button>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Корзина</h1>
      <Table
        columns={columns}
        data={cart?.items ?? []}
        rowKey={(row) => row.productId}
        loading={loading}
        empty="Корзина пуста. Загляните в каталог!"
      />

      {!isEmpty && (
        <>
          <div className={styles.totals}>
            <span>
                Итого: <strong>{formatMoney(cart!.totalAmount)}</strong>
            </span>
            <span className={styles.vat}>в т.ч. НДС: {formatMoney(cart!.totalVat)}</span>
          </div>

          <div className={styles.checkout}>
            <h2>Оформление</h2>
            <Select
              label="Получение"
              value={deliveryType}
              onChange={(event) => setDeliveryType(event.target.value as "PICKUP" | "DELIVERY")}
              options={[
                { value: "PICKUP", label: "Самовывоз" },
                { value: "DELIVERY", label: "Доставка" },
              ]}
            />
            {deliveryType === "DELIVERY" && (
              <Input
                label="Адрес доставки"
                value={address}
                onChange={(event) => setAddress(event.target.value)}
                placeholder="г. Минск, ул. …"
              />
            )}
            <Input
              label="Имя получателя"
              value={recipientName}
              onChange={(event) => setRecipientName(event.target.value)}
            />
            <Input
              label="Телефон получателя"
              value={recipientPhone}
              onChange={(event) => setRecipientPhone(event.target.value)}
              placeholder="+375 29 …"
            />
            <Input label="Комментарий" value={note} onChange={(event) => setNote(event.target.value)} />
            <div className={styles.actions}>
              <Button onClick={submit} loading={submitting}>
                Подтвердить заказ
              </Button>
              <Button
                variant="secondary"
                onClick={async () => {
                  setCart(await clearCart());
                }}
              >
                Очистить корзину
              </Button>
            </div>
            <p className={styles.hint}>
              {isRetail
                ? "Оплата при получении или самовывозе. Заказ попадёт в пул «не назначено» — менеджер свяжется с вами."
                : "Заказ закрепится за менеджером вашей компании."}
            </p>
          </div>
        </>
      )}
      {isEmpty && (
        <p>
          <Link to="/cabinet/catalog">Перейти в каталог</Link>
        </p>
      )}
    </div>
  );
}
