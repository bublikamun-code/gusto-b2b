import type { Order } from "../api/cart";

export interface FrequentItem {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  totalQuantity: number;
  orderCount: number;
}

/**
 * Топ позиций из истории заказов (S23, «Часто заказываемые»): считаем по
 * снапшотам позиций уже видимых клиенту заказов. Пустые/служебные позиции
 * (без productId) пропускаем.
 */
export function topFrequentItems(orders: Order[], limit = 6): FrequentItem[] {
  const byProduct = new Map<string, FrequentItem>();
  for (const order of orders) {
    for (const item of order.items ?? []) {
      if (!item.productId) continue;
      const existing = byProduct.get(item.productId);
      if (existing) {
        existing.totalQuantity += item.quantity;
        existing.orderCount += 1;
      } else {
        byProduct.set(item.productId, {
          productId: item.productId,
          sku: item.sku ?? "",
          name: item.productName ?? "",
          unit: item.unit ?? "",
          totalQuantity: item.quantity,
          orderCount: 1,
        });
      }
    }
  }
  return [...byProduct.values()]
    .sort((a, b) => b.orderCount - a.orderCount || b.totalQuantity - a.totalQuantity)
    .slice(0, limit);
}
