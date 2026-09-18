import type { ReactNode } from "react";
import { Badge } from "../components/ui";

export type OrderStatus =
  | "NEW"
  | "CONFIRMED"
  | "PROCESSING"
  | "READY"
  | "SHIPPED"
  | "COMPLETED"
  | "CANCELLED";

/** Все статусы в порядке жизненного цикла (словарь 2.8). */
export const ORDER_STATUSES: OrderStatus[] = [
  "NEW",
  "CONFIRMED",
  "PROCESSING",
  "READY",
  "SHIPPED",
  "COMPLETED",
  "CANCELLED",
];

/**
 * Зеркало статус-машины бэкенда (S22) для UI: показывает менеджеру только
 * допустимые переходы; источник истины — бэкенд (409 при нарушении).
 */
export const ALLOWED_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  NEW: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["READY", "CANCELLED"],
  READY: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["COMPLETED"],
  COMPLETED: [],
  CANCELLED: [],
};

export function orderStatusLabel(status: OrderStatus | undefined): string {
  switch (status) {
    case "NEW":
      return "Новый";
    case "CONFIRMED":
      return "Подтверждён";
    case "PROCESSING":
      return "В сборке";
    case "READY":
      return "Готов к выдаче";
    case "SHIPPED":
      return "Отгружен";
    case "COMPLETED":
      return "Выполнен";
    case "CANCELLED":
      return "Отменён";
    default:
      return status ?? "—";
  }
}

export function orderStatusBadge(status: OrderStatus | undefined): ReactNode {
  switch (status) {
    case "NEW":
      return <Badge variant="accent">{orderStatusLabel(status)}</Badge>;
    case "COMPLETED":
      return <Badge variant="success">{orderStatusLabel(status)}</Badge>;
    case "CANCELLED":
      return <Badge variant="outline">{orderStatusLabel(status)}</Badge>;
    case "PROCESSING":
    case "READY":
      return <Badge variant="warning">{orderStatusLabel(status)}</Badge>;
    default:
      return <Badge variant="neutral">{orderStatusLabel(status)}</Badge>;
  }
}

export function allowedNextStatuses(status: OrderStatus | undefined): OrderStatus[] {
  if (!status) return [];
  return ALLOWED_TRANSITIONS[status] ?? [];
}
