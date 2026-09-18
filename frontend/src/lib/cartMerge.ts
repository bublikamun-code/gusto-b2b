import type { CartItem } from "../store/cartStore";

export interface LocalCartLine {
  productId: string;
  quantity: number;
}

/**
 * Перенос локальной корзины в серверную (примечание к S16, S21):
 * позиции группируются по productId (количество суммируется).
 */
export function mergeLocalCartItems(items: CartItem[]): LocalCartLine[] {
  const byProduct = new Map<string, number>();
  for (const item of items) {
    byProduct.set(item.productId, (byProduct.get(item.productId) ?? 0) + item.quantity);
  }
  return [...byProduct.entries()].map(([productId, quantity]) => ({ productId, quantity }));
}
