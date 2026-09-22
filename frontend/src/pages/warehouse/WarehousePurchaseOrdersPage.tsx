import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ConfirmModal,
  Input,
  Modal,
  Pagination,
  Select,
  Table,
  useToast,
} from '../../components/ui';
import {
  cancelPurchaseOrder,
  createPurchaseOrder,
  listProductRefs,
  listPurchaseOrders,
  listSuppliers,
  sendPurchaseOrder,
  type CatalogProductRef,
  type PurchaseOrder,
  type Supplier,
} from '../../api/warehouse';
import { PURCHASE_STATUS_LABELS } from '../../lib/warehouse';
import { formatMoney } from '../../lib/format';
import styles from './WarehousePages.module.scss';

interface DraftItem {
  productId: string;
  quantity: string;
  purchasePrice: string;
}

export default function WarehousePurchaseOrdersPage() {
  const { push } = useToast();
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<CatalogProductRef[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [supplierId, setSupplierId] = useState('');
  const [expectedDate, setExpectedDate] = useState('');
  const [items, setItems] = useState<DraftItem[]>([
    { productId: '', quantity: '1', purchasePrice: '' },
  ]);
  const [saving, setSaving] = useState(false);
  const [pendingAction, setPendingAction] = useState<{
    action: 'send' | 'cancel';
    order: PurchaseOrder;
  } | null>(null);
  const [acting, setActing] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listPurchaseOrders({ status: statusFilter, page })
      .then((result) => {
        setOrders(result.items);
        setTotal(result.total);
      })
      .catch((err) => push((err as Error).message, 'error'))
      .finally(() => setLoading(false));
  }, [statusFilter, page, push]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    listSuppliers()
      .then((result) => setSuppliers(result.items))
      .catch(() => undefined);
    listProductRefs()
      .then((result) => setProducts(result.items))
      .catch(() => undefined);
  }, []);

  const supplierOptions = [
    { value: '', label: '— поставщик —' },
    ...suppliers.filter((s) => s.isActive).map((s) => ({ value: s.id, label: s.name })),
  ];
  const productOptions = [
    { value: '', label: '— товар —' },
    ...products.map((product) => ({
      value: product.id,
      label: `${product.sku} · ${product.name}`,
    })),
  ];

  const draftTotal = items.reduce(
    (acc, item) => acc + (Number(item.quantity) || 0) * (Number(item.purchasePrice) || 0),
    0,
  );

  const submit = async () => {
    if (!supplierId) {
      push('Выберите поставщика', 'error');
      return;
    }
    if (
      items.some(
        (item) => !item.productId || Number(item.quantity) <= 0 || Number(item.purchasePrice) <= 0,
      )
    ) {
      push('Заполните позиции: товар, количество и закупочная цена', 'error');
      return;
    }
    setSaving(true);
    try {
      const order = await createPurchaseOrder({
        supplierId,
        expectedDate: expectedDate || undefined,
        items: items.map((item) => ({
          productId: item.productId,
          quantity: Number(item.quantity),
          purchasePrice: Number(item.purchasePrice),
        })),
      });
      // черновик отправляется отдельным подтверждаемым действием «Отправить»
      push(`${order.number} создан как черновик`, 'success');
      setModalOpen(false);
      setSupplierId('');
      setExpectedDate('');
      setItems([{ productId: '', quantity: '1', purchasePrice: '' }]);
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const runAction = async () => {
    if (!pendingAction) return;
    const { action, order } = pendingAction;
    setActing(true);
    try {
      if (action === 'send') {
        await sendPurchaseOrder(order.id);
        push(`${order.number} отправлен`, 'success');
      } else {
        await cancelPurchaseOrder(order.id);
        push(`${order.number} отменён`, 'info');
      }
      setPendingAction(null);
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setActing(false);
    }
  };

  const columns = [
    {
      key: 'number',
      title: 'Номер',
      render: (row: PurchaseOrder) => <span className={styles.number}>{row.number}</span>,
    },
    { key: 'supplierName', title: 'Поставщик' },
    {
      key: 'status',
      title: 'Статус',
      render: (row: PurchaseOrder) => (
        <Badge
          variant={
            row.status === 'RECEIVED'
              ? 'success'
              : row.status === 'PARTIAL'
                ? 'warning'
                : row.status === 'CANCELLED'
                  ? 'neutral'
                  : 'outline'
          }
        >
          {PURCHASE_STATUS_LABELS[row.status]}
        </Badge>
      ),
    },
    {
      key: 'totalAmount',
      title: 'Сумма',
      align: 'right' as const,
      render: (row: PurchaseOrder) => formatMoney(row.totalAmount),
    },
    {
      key: 'items',
      title: 'Принято',
      render: (row: PurchaseOrder) =>
        row.items
          .map((item) => `${item.sku ?? item.productId}: ${item.receivedQuantity}/${item.quantity}`)
          .join(', '),
    },
    {
      key: 'actions',
      title: '',
      render: (row: PurchaseOrder) =>
        row.status === 'DRAFT' || row.status === 'SENT' ? (
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            {row.status === 'DRAFT' && (
              <Button size="sm" onClick={() => setPendingAction({ action: 'send', order: row })}>
                Отправить
              </Button>
            )}
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setPendingAction({ action: 'cancel', order: row })}
            >
              Отменить
            </Button>
          </div>
        ) : null,
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Заказы поставщикам</h1>
        <Button onClick={() => setModalOpen(true)}>Новый заказ</Button>
      </div>
      <div className={styles.toolbar}>
        <Select
          label="Статус"
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(0);
          }}
          options={[
            { value: '', label: 'Все статусы' },
            ...Object.entries(PURCHASE_STATUS_LABELS).map(([value, label]) => ({ value, label })),
          ]}
        />
      </div>
      <Table
        columns={columns}
        data={orders}
        rowKey={(row) => row.id}
        loading={loading}
        empty="Заказов нет"
      />
      <Pagination page={page + 1} size={20} total={total} onChange={(next) => setPage(next - 1)} />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Новый заказ поставщику">
        <div className={styles.formColumn}>
          <Select
            label="Поставщик"
            value={supplierId}
            onChange={(event) => setSupplierId(event.target.value)}
            options={supplierOptions}
          />
          <Input
            label="Ожидаемая дата"
            type="date"
            value={expectedDate}
            onChange={(event) => setExpectedDate(event.target.value)}
          />
          <div className={styles.itemsEditor}>
            <strong>Позиции</strong>
            {items.map((item, index) => (
              <div key={index} className={styles.itemRow}>
                <Select
                  label="Товар"
                  value={item.productId}
                  onChange={(event) => {
                    const next = [...items];
                    next[index] = { ...item, productId: event.target.value };
                    setItems(next);
                  }}
                  options={productOptions}
                />
                <Input
                  label="Кол-во"
                  type="number"
                  step="0.001"
                  min="0"
                  value={item.quantity}
                  onChange={(event) => {
                    const next = [...items];
                    next[index] = { ...item, quantity: event.target.value };
                    setItems(next);
                  }}
                />
                <Input
                  label="Цена закупки"
                  type="number"
                  step="0.01"
                  min="0"
                  value={item.purchasePrice}
                  onChange={(event) => {
                    const next = [...items];
                    next[index] = { ...item, purchasePrice: event.target.value };
                    setItems(next);
                  }}
                />
                <Button
                  variant="secondary"
                  aria-label="Удалить позицию"
                  onClick={() => setItems(items.filter((_, i) => i !== index))}
                >
                  ✕
                </Button>
              </div>
            ))}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Button
                variant="secondary"
                onClick={() =>
                  setItems([...items, { productId: '', quantity: '1', purchasePrice: '' }])
                }
              >
                + позиция
              </Button>
              <strong>Итого: {formatMoney(draftTotal)}</strong>
            </div>
          </div>
          <Button onClick={submit} loading={saving}>
            Создать черновик
          </Button>
        </div>
      </Modal>

      <ConfirmModal
        open={pendingAction !== null}
        title={pendingAction?.action === 'send' ? 'Отправить заказ' : 'Отменить заказ'}
        confirmLabel={pendingAction?.action === 'send' ? 'Отправить' : 'Отменить'}
        loading={acting}
        onClose={() => setPendingAction(null)}
        onConfirm={runAction}
      >
        <p>
          {pendingAction?.action === 'send'
            ? `Отправить заказ ${pendingAction.order.number} поставщику ${pendingAction.order.supplierName}? После отправки состав заказа изменить нельзя.`
            : `Отменить заказ ${pendingAction?.order.number}? Действие необратимо, принять по нему товар будет нельзя.`}
        </p>
      </ConfirmModal>
    </div>
  );
}
