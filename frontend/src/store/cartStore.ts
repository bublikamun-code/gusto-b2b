import { create } from "zustand";
import { persist } from "zustand/middleware";
import { roundToStep } from "../lib/units";

export interface CartItem {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  price: number;
  quantity: number;
  /** Шаг количества весового товара (weight_per_unit, S19.1); без шага — свободное. */
  step?: number | null;
}

interface CartState {
  items: CartItem[];
  ownerId: string | null;
  addItem: (item: Omit<CartItem, "quantity">, quantity?: number) => void;
  setQuantity: (sku: string, quantity: number) => void;
  removeItem: (sku: string) => void;
  clear: () => void;
  /** При смене пользователя корзина предыдущего владельца не показывается новому */
  setOwner: (ownerId: string | null) => void;
}

const CART_STORAGE_KEY = "gusto-cart";

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      ownerId: null,

      addItem(product, quantity = 1) {
        const step = product.step ?? null;
        const stepped = roundToStep(quantity, step);
        if (!Number.isFinite(stepped) || stepped <= 0) return;
        const items = [...get().items];
        const index = items.findIndex((item) => item.sku === product.sku);
        if (index >= 0) {
          const merged = roundToStep(items[index].quantity + stepped, step);
          items[index] = { ...items[index], quantity: merged };
        } else {
          items.push({ ...product, step, quantity: stepped });
        }
        set({ items });
      },

      setQuantity(sku, quantity) {
        const items = [...get().items];
        const index = items.findIndex((item) => item.sku === sku);
        if (index < 0) return;
        const stepped = roundToStep(quantity, items[index].step ?? null);
        if (stepped <= 0) {
          items.splice(index, 1);
        } else {
          items[index] = { ...items[index], quantity: stepped };
        }
        set({ items });
      },

      removeItem(sku) {
        set({ items: get().items.filter((item) => item.sku !== sku) });
      },

      clear() {
        set({ items: [] });
      },

      setOwner(ownerId) {
        if (get().ownerId !== ownerId) {
          set({ items: [], ownerId });
        }
      },
    }),
    {
      name: CART_STORAGE_KEY,
    },
  ),
);

export function selectCartTotalCount(items: CartItem[]): number {
  return items.reduce((acc, item) => acc + item.quantity, 0);
}

export function selectCartTotalSum(items: CartItem[]): number {
  return items.reduce((acc, item) => acc + item.price * item.quantity, 0);
}
