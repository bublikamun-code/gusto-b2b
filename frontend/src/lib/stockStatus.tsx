import type { ReactNode } from "react";
import { Badge } from "../components/ui";
import type { components } from "../api/schema";

type StockStatus = components["schemas"]["StockStatus"];

/** Подписи и бейджи статуса наличия (S19): данные — из доступного остатка (S18). */
export function stockStatusLabel(status: StockStatus | undefined): string {
  switch (status) {
    case "IN_STOCK":
      return "В наличии";
    case "PREORDER":
      return "Под заказ";
    default:
      return "Уточните наличие";
  }
}

export function stockStatusBadge(status: StockStatus | undefined): ReactNode {
  switch (status) {
    case "IN_STOCK":
      return <Badge variant="success">{stockStatusLabel(status)}</Badge>;
    case "PREORDER":
      return <Badge variant="warning">{stockStatusLabel(status)}</Badge>;
    default:
      return <Badge variant="neutral">{stockStatusLabel(status)}</Badge>;
  }
}
