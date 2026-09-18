import { describe, expect, it } from "vitest";
import { mergeLocalCartItems } from "./cartMerge";
import type { CartItem } from "../store/cartStore";

const line = (overrides: Partial<CartItem>): CartItem => ({
  productId: "p-1",
  sku: "A-100",
  name: "Филе",
  unit: "кг",
  price: 10,
  quantity: 1,
  ...overrides,
});

describe("mergeLocalCartItems", () => {
  it("groups duplicates by productId summing quantity", () => {
    const merged = mergeLocalCartItems([
      line({ productId: "p-1", quantity: 2 }),
      line({ productId: "p-1", quantity: 1.5 }),
      line({ productId: "p-2", quantity: 3 }),
    ]);
    expect(merged).toEqual([
      { productId: "p-1", quantity: 3.5 },
      { productId: "p-2", quantity: 3 },
    ]);
  });

  it("keeps weight step of the product with the first occurrence", () => {
    // шаг не мешает переносу: выравнивание делает корзина/сервер при заказе
    const merged = mergeLocalCartItems([line({ productId: "p-3", quantity: 0.5, step: 0.5 })]);
    expect(merged).toEqual([{ productId: "p-3", quantity: 0.5 }]);
  });

  it("returns empty for empty cart", () => {
    expect(mergeLocalCartItems([])).toEqual([]);
  });
});
