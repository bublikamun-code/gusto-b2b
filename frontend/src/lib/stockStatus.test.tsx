import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { stockStatusLabel, stockStatusBadge } from "./stockStatus";
import type { components } from "../api/schema";

type StockStatus = components["schemas"]["StockStatus"];

describe("stockStatusLabel", () => {
  it("maps known statuses", () => {
    expect(stockStatusLabel("IN_STOCK")).toBe("В наличии");
    expect(stockStatusLabel("PREORDER")).toBe("Под заказ");
  });

  it("falls back for missing data", () => {
    expect(stockStatusLabel(undefined)).toBe("Уточните наличие");
  });
});

describe("stockStatusBadge", () => {
  it("renders success badge for in-stock and warning for preorder", () => {
    const inStock = render(<div data-testid="a">{stockStatusBadge("IN_STOCK" as StockStatus)}</div>);
    expect(inStock.getByTestId("a").textContent).toBe("В наличии");

    const preorder = render(<div data-testid="b">{stockStatusBadge("PREORDER" as StockStatus)}</div>);
    expect(preorder.getByTestId("b").textContent).toBe("Под заказ");
  });
});
