import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Badge, Button, Card, Table, useToast } from "../../components/ui";
import { addOrderItemsToCart, listOrders, type Order } from "../../api/orders";
import { orderStatusBadge, type OrderStatus } from "../../lib/orderStatus";
import { topFrequentItems } from "../../lib/frequentItems";
import { formatMoney } from "../../lib/format";
import { putCartItem } from "../../api/cart";
import { useAuthStore } from "../../store/authStore";
import styles from "./CabinetOrdersPage.module.scss";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function CabinetOrdersPage() {
  const { push } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [details, setDetails] = useState<Order | null>(null);
  const [repeating, setRepeating] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    listOrders(0, 50)
      .then((page) => setOrders(page.items))
      .catch((err) => push((err as Error).message, "error"))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const repeat = async (order: Order) => {
    setRepeating(order.id);
    try {
      await addOrderItemsToCart(order.items);
      queryClient.invalidateQueries({ queryKey: ["cart"] });
      push("Позиции добавлены в корзину", "success");
      navigate("/cabinet/cart");
    } catch (err) {
      push((err as Error).message, "error");
    } finally {
      setRepeating(null);
    }
  };

  const addFrequent = async (productId: string) => {
    try {
      await putCartItem(productId, 1);
      queryClient.invalidateQueries({ queryKey: ["cart"] });
      push("Добавлено в корзину", "success");
    } catch (err) {
      push((err as Error).message, "error");
    }
  };

  const frequent = user?.role === "CUSTOMER_LEGAL" ? topFrequentItems(orders, 6) : [];

  const columns = [
    { key: "number", title: "Номер", render: (o: Order) => <strong>{o.number}</strong> },
    { key: "createdAt", title: "Дата", render: (o: Order) => formatDate(o.createdAt) },
    { key: "status", title: "Статус", render: (o: Order) => orderStatusBadge(o.status as OrderStatus) },
    {
      key: "total",
      title: "Сумма",
      align: "right" as const,
      render: (o: Order) => formatMoney(o.totalAmount),
    },
    {
      key: "actions",
      title: "",
      align: "right" as const,
      render: (o: Order) => (
        <div className={styles.rowActions}>
          <Button variant="secondary" onClick={() => setDetails(o)}>
            Детали
          </Button>
          <Button
            variant="accent"
            loading={repeating === o.id}
            disabled={o.status === "CANCELLED" && o.items.length === 0}
            onClick={() => repeat(o)}
          >
            Повторить
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Мои заказы</h1>
        <nav className={styles.nav}>
          <Link to="/cabinet/catalog">Каталог</Link>
          <Link to="/cabinet/cart">Корзина</Link>
        </nav>
      </header>

      {frequent.length > 0 && (
        <Card className={styles.frequent}>
          <h2>Часто заказываемые</h2>
          <div className={styles.frequentList}>
            {frequent.map((item) => (
              <div key={item.productId} className={styles.frequentItem}>
                <div>
                  <strong>{item.name}</strong>
                  <span className={styles.frequentMeta}>
                    {item.sku} · заказывали {item.orderCount} раз(а)
                  </span>
                </div>
                <Button variant="accent" onClick={() => addFrequent(item.productId)}>
                  В корзину
                </Button>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Table<Order>
        columns={columns}
        data={orders}
        rowKey={(o) => o.id}
        loading={loading}
        empty="Заказов пока нет — начните с каталога"
      />

      {details && (
        <div className={styles.overlay} onClick={() => setDetails(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHead}>
              <h2>Заказ {details.number}</h2>
              {orderStatusBadge(details.status as OrderStatus)}
              <Button variant="secondary" onClick={() => setDetails(null)}>
                Закрыть
              </Button>
            </div>
            <p className={styles.meta}>
              {formatDate(details.createdAt)} ·{" "}
              {details.deliveryType === "DELIVERY"
                ? `Доставка: ${details.deliveryAddress ?? "—"}`
                : "Самовывоз"}
            </p>
            {details.recipientName && (
              <p className={styles.meta}>
                Получатель: {details.recipientName}, {details.recipientPhone}
              </p>
            )}
            <table className={styles.items}>
              <thead>
                <tr>
                  <th>Товар</th>
                  <th>Кол-во</th>
                  <th>Цена</th>
                  <th>Сумма</th>
                </tr>
              </thead>
              <tbody>
                {details.items.map((item) => (
                  <tr key={item.productId}>
                    <td>
                      {item.productName}
                      <span className={styles.sku}> {item.sku}</span>
                    </td>
                    <td>
                      {item.quantity} {item.unit}
                    </td>
                    <td>{formatMoney(item.unitPrice)}</td>
                    <td>{formatMoney(item.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className={styles.total}>
              Итого: <strong>{formatMoney(details.totalAmount)}</strong>{" "}
              <Badge variant="neutral">в т.ч. НДС {formatMoney(details.totalVat)}</Badge>
            </p>
            <Button
              variant="accent"
              loading={repeating === details.id}
              onClick={() => repeat(details)}
            >
              Повторить заказ
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
