import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Button, ConfirmModal, Input, Select, Table, useToast } from '../../components/ui';
import {
  clearCart,
  createOrder,
  getCart,
  putCartItem,
  type CartLine,
  type ServerCart,
} from '../../api/cart';
import { useAuthStore } from '../../store/authStore';
import { formatMoney } from '../../lib/format';
import { parseQuantityInput } from '../../lib/quantityInput';
import styles from './CabinetCartPage.module.scss';

export default function CabinetCartPage() {
  const { push } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const [cart, setCart] = useState<ServerCart | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState<{ number: string } | null>(null);
  const [confirmingClear, setConfirmingClear] = useState(false);
  const [clearing, setClearing] = useState(false);

  const [deliveryType, setDeliveryType] = useState<'PICKUP' | 'DELIVERY'>('PICKUP');
  const [address, setAddress] = useState('');
  const [recipientName, setRecipientName] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [note, setNote] = useState('');

  // plan-04: локальный ввод количества коммитится по blur/Enter, а не на keystroke.
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const busyRef = useRef(new Set<string>());
  const [busyIds, setBusyIds] = useState<ReadonlySet<string>>(new Set());

  const load = useCallback(() => {
    setLoading(true);
    getCart()
      .then(setCart)
      .catch((err) => push((err as Error).message, 'error'))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const isRetail = user?.role === 'CUSTOMER_INDIVIDUAL';
  const isEmpty = !cart || cart.items.length === 0;

  const setDraft = (productId: string, value: string) => {
    setDrafts((prev) => ({ ...prev, [productId]: value }));
  };

  const clearDraft = (productId: string) => {
    setDrafts((prev) => {
      if (!(productId in prev)) return prev;
      const next = { ...prev };
      delete next[productId];
      return next;
    });
  };

  const markBusy = (productId: string, busy: boolean) => {
    if (busy) busyRef.current.add(productId);
    else busyRef.current.delete(productId);
    setBusyIds(new Set(busyRef.current));
  };

  const changeQuantity = async (productId: string, quantity: number) => {
    if (busyRef.current.has(productId)) return;
    try {
      setCart(await putCartItem(productId, quantity));
    } catch (err) {
      push((err as Error).message, 'error');
    }
  };

  const commitQuantity = async (productId: string) => {
    const draft = drafts[productId];
    if (draft === undefined || busyRef.current.has(productId)) return;
    const parsed = parseQuantityInput(draft);
    if (parsed.kind === 'empty' || parsed.kind === 'invalid') {
      push('Введите количество', 'error');
      clearDraft(productId);
      return;
    }
    if (parsed.kind === 'zero') {
      push('Используйте «Убрать», чтобы удалить позицию', 'error');
      clearDraft(productId);
      return;
    }
    markBusy(productId, true);
    try {
      setCart(await putCartItem(productId, parsed.value));
      clearDraft(productId);
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      markBusy(productId, false);
    }
  };

  const submit = async () => {
    if (deliveryType === 'DELIVERY' && !address.trim()) {
      push('Укажите адрес доставки', 'error');
      return;
    }
    if (!recipientName.trim() || !recipientPhone.trim()) {
      push('Укажите имя и телефон получателя', 'error');
      return;
    }
    setSubmitting(true);
    try {
      const order = await createOrder(
        {
          deliveryType,
          deliveryAddress: deliveryType === 'DELIVERY' ? address : undefined,
          recipientName,
          recipientPhone,
          note: note || undefined,
        },
        crypto.randomUUID(),
      );
      setDone({ number: order.number });
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      push(`Заказ ${order.number} создан`, 'success');
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const clear = async () => {
    setClearing(true);
    try {
      setCart(await clearCart());
      setConfirmingClear(false);
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setClearing(false);
    }
  };

  if (done) {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>Заказ {done.number} принят</h1>
        <p>Менеджер свяжется с вами для подтверждения. Заказ появился в истории заказов.</p>
        <div className={styles.actions}>
          <Button onClick={() => navigate('/cabinet/cart', { replace: true })}>К корзине</Button>
        </div>
      </div>
    );
  }

  const columns = [
    { key: 'sku', title: 'Артикул' },
    { key: 'productName', title: 'Товар' },
    { key: 'unit', title: 'Ед.' },
    {
      key: 'quantity',
      title: 'Кол-во',
      align: 'center' as const,
      render: (row: ServerCart['items'][number]) => (
        <Input
          type="number"
          min={0.001}
          step={0.001}
          value={drafts[row.productId] ?? String(row.quantity)}
          onChange={(event) => setDraft(row.productId, event.target.value)}
          onBlur={() => commitQuantity(row.productId)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') commitQuantity(row.productId);
            if (event.key === 'Escape') clearDraft(row.productId);
          }}
          disabled={busyIds.has(row.productId)}
          aria-label={`Количество: ${row.productName}`}
          className={styles.quantityInput}
        />
      ),
    },
    {
      key: 'unitPrice',
      title: 'Цена',
      align: 'right' as const,
      render: (row: CartLine) => formatMoney(row.unitPrice),
    },
    {
      key: 'total',
      title: 'Сумма',
      align: 'right' as const,
      render: (row: CartLine) => formatMoney(row.total),
    },
    {
      key: 'actions',
      title: '',
      render: (row: ServerCart['items'][number]) => (
        <Button
          size="sm"
          variant="secondary"
          disabled={busyIds.has(row.productId)}
          onClick={() => changeQuantity(row.productId, 0)}
        >
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
              onChange={(event) => setDeliveryType(event.target.value as 'PICKUP' | 'DELIVERY')}
              options={[
                { value: 'PICKUP', label: 'Самовывоз' },
                { value: 'DELIVERY', label: 'Доставка' },
              ]}
            />
            {deliveryType === 'DELIVERY' && (
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
            <Input
              label="Комментарий"
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
            <div className={styles.actions}>
              <Button onClick={submit} loading={submitting}>
                Подтвердить заказ
              </Button>
              <Button variant="secondary" onClick={() => setConfirmingClear(true)}>
                Очистить корзину
              </Button>
            </div>
            <p className={styles.hint}>
              {isRetail
                ? 'Оплата при получении или самовывозе. Заказ попадёт в пул «не назначено» — менеджер свяжется с вами.'
                : 'Заказ закрепится за менеджером вашей компании.'}
            </p>
          </div>
        </>
      )}
      {isEmpty && <p className={styles.emptyHint}>Каталог и навигация — в меню сверху</p>}

      <ConfirmModal
        open={confirmingClear}
        title="Очистить корзину"
        confirmLabel="Очистить"
        loading={clearing}
        onClose={() => setConfirmingClear(false)}
        onConfirm={clear}
      >
        <p>Удалить все позиции из корзины? Придётся добавлять их заново из каталога.</p>
      </ConfirmModal>
    </div>
  );
}
