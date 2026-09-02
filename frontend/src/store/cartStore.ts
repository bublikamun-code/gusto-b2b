import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface CartItem {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  price: number;
  quantity: number;
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
        if (!Number.isFinite(quantity) || quantity <= 0) return;
        const items = [...get().items];
        const index = items.findIndex((item) => item.sku === product.sku);
        if (index >= 0) {
          items[index] = { ...items[index], quantity: items[index].quantity + quantity };
        } else {
          items.push({ ...product, quantity });
        }
        set({ items });
      },

      setQuantity(sku, quantity) {
        const items = [...get().items];
        const index = items.findIndex((item) => item.sku === sku);
        if (index < 0) return;
        if (quantity <= 0) {
          items.splice(index, 1);
        } else {
          items[index] = { ...items[index], quantity };
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
