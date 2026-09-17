import { beforeEach, describe, expect, it } from "vitest";
import {
  selectCartTotalCount,
  selectCartTotalSum,
  useCartStore,
  type CartItem,
} from "./cartStore";

const product = (overrides: Partial<CartItem> = {}): Omit<CartItem, "quantity"> => ({
  productId: "p-1",
  sku: "A-100",
  name: "Филе куриное",
  unit: "кг",
  price: 12.5,
  ...overrides,
});

describe("cartStore", () => {
  beforeEach(() => {
    localStorage.clear();
    useCartStore.setState({ items: [], ownerId: null });
  });

  it("adds a new item", () => {
    useCartStore.getState().addItem(product(), 2);
    expect(useCartStore.getState().items).toHaveLength(1);
    expect(useCartStore.getState().items[0]).toMatchObject({ sku: "A-100", quantity: 2 });
  });

  it("accumulates quantity for the same sku", () => {
    useCartStore.getState().addItem(product(), 1);
    useCartStore.getState().addItem(product({ price: 99 }), 3);
    const items = useCartStore.getState().items;
    expect(items).toHaveLength(1);
    expect(items[0].quantity).toBe(4);
    // цена и название — из первого добавления
    expect(items[0].price).toBe(12.5);
  });

  it("ignores non-positive or broken quantity", () => {
    useCartStore.getState().addItem(product(), 0);
    useCartStore.getState().addItem(product(), -1);
    useCartStore.getState().addItem(product(), Number.NaN);
    expect(useCartStore.getState().items).toHaveLength(0);
  });

  it("setQuantity updates and zero removes the item", () => {
    useCartStore.getState().addItem(product(), 1);
    useCartStore.getState().setQuantity("A-100", 5);
    expect(useCartStore.getState().items[0].quantity).toBe(5);
    useCartStore.getState().setQuantity("A-100", 0);
    expect(useCartStore.getState().items).toHaveLength(0);
  });

  it("setQuantity ignores unknown sku", () => {
    useCartStore.getState().addItem(product(), 1);
    useCartStore.getState().setQuantity("nope", 7);
    expect(useCartStore.getState().items[0].quantity).toBe(1);
  });

  it("removeItem deletes only the target sku", () => {
    useCartStore.getState().addItem(product(), 1);
    useCartStore.getState().addItem(product({ sku: "B-200", productId: "p-2" }), 1);
    useCartStore.getState().removeItem("A-100");
    expect(useCartStore.getState().items.map((i) => i.sku)).toEqual(["B-200"]);
  });

  it("setOwner keeps items for the same owner and clears them on switch", () => {
    useCartStore.getState().setOwner("user-1");
    useCartStore.getState().addItem(product(), 2);

    useCartStore.getState().setOwner("user-1");
    expect(useCartStore.getState().items).toHaveLength(1); // тот же владелец — корзина на месте

    useCartStore.getState().setOwner("user-2");
    expect(useCartStore.getState().items).toHaveLength(0);
    expect(useCartStore.getState().ownerId).toBe("user-2");

    // разлогин: корзина тоже не должна переживать смену аккаунта
    useCartStore.getState().addItem(product(), 1);
    useCartStore.getState().setOwner(null);
    expect(useCartStore.getState().items).toHaveLength(0);
  });

  it("selectors compute count and sum", () => {
    useCartStore.getState().addItem(product(), 2); // 12.5 * 2
    useCartStore.getState().addItem(product({ sku: "B-200", productId: "p-2", price: 7 }), 3); // 7 * 3
    const items = useCartStore.getState().items;
    expect(selectCartTotalCount(items)).toBe(5);
    expect(selectCartTotalSum(items)).toBeCloseTo(46, 10);
    expect(selectCartTotalSum([])).toBe(0);
    expect(selectCartTotalCount([])).toBe(0);
  });
});
