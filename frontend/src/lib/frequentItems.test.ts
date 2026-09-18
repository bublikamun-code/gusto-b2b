import { describe, expect, it } from "vitest";
import { topFrequentItems } from "./frequentItems";
import type { Order } from "../api/cart";

function order(items: Order["items"]): Order {
  return {
    id: "o",
    number: "З-2026-00001",
    status: "COMPLETED",
    deliveryType: "PICKUP",
    totalAmount: 0,
    totalVat: 0,
    createdAt: "2026-09-01T10:00:00Z",
    items,
  };
}

const steak = { productId: "p-steak", sku: "steyk-ribay", productName: "Стейк рибай", unit: "кг", quantity: 2, unitPrice: 42.5, total: 85 };
const bedro = { productId: "p-bedro", sku: "bedro", productName: "Бедро куриное", unit: "кг", quantity: 3, unitPrice: 20, total: 60 };
const farsh = { productId: "p-farsh", sku: "farsh", productName: "Фарш", unit: "кг", quantity: 1, unitPrice: 18, total: 18 };

describe("topFrequentItems (S23 «Часто заказываемые»)", () => {
  it("ранжирует по числу заказов, затем по количеству", () => {
    const orders = [
      order([steak, bedro]),
      order([steak, farsh]),
      order([bedro]),
    ];
    const top = topFrequentItems(orders, 2);
    // steak и bedro заказаны по 2 раза, farsh — 1; steak впереди по суммарному количеству (4 против 5? нет: steak 2+2=4, bedro 3+3=6)
    expect(top[0].productId).toBe("p-bedro");
    expect(top[1].productId).toBe("p-steak");
    expect(top).toHaveLength(2);
  });

  it("суммирует количество и считает повторы", () => {
    const top = topFrequentItems([order([steak]), order([steak])]);
    expect(top).toHaveLength(1);
    expect(top[0].totalQuantity).toBe(4);
    expect(top[0].orderCount).toBe(2);
  });

  it("ограничивает выборку и пропускает позиции без productId", () => {
    const broken = { ...steak, productId: "" };
    const orders = [order([broken, farsh, bedro])];
    const top = topFrequentItems(orders, 10);
    // одна закупка у обоих: bedro впереди по суммарному количеству (3 против 1)
    expect(top.map((item) => item.productId)).toEqual(["p-bedro", "p-farsh"]);
  });

  it("пустая история — пустой результат", () => {
    expect(topFrequentItems([])).toEqual([]);
  });
});
