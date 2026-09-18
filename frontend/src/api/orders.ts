import { apiListRequest, apiRequest } from "./client";
import type { Order } from "./cart";
import type { OrderStatus } from "../lib/orderStatus";

export type { Order, OrderStatus };

export function listOrders(page = 0, size = 20) {
  return apiListRequest<Order>(`/orders?page=${page}&size=${size}`);
}

export function getOrder(id: string) {
  return apiRequest<Order>(`/orders/${encodeURIComponent(id)}`);
}

// ----- Менеджер (S22/S23) ----------------------------------------------------

export interface ManagerOrderFilters {
  scope?: "mine" | "unassigned" | "all";
  status?: OrderStatus;
  page?: number;
  size?: number;
}

function buildQuery(filters: Record<string, unknown>): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === "" || value === null) return;
    params.set(key, String(value));
  });
  const query = params.toString();
  return query ? `?${query}` : "";
}

export function listManagerOrders(filters: ManagerOrderFilters = {}) {
  return apiListRequest<Order>(`/manager/orders${buildQuery(filters as Record<string, unknown>)}`);
}

export function takeOrder(id: string) {
  return apiRequest<Order>(`/manager/orders/${encodeURIComponent(id)}/take`, { method: "POST" });
}

export function changeOrderStatus(id: string, status: OrderStatus) {
  return apiRequest<Order>(`/manager/orders/${encodeURIComponent(id)}/status`, {
    method: "PUT",
    body: { status },
  });
}

/**
 * Повтор заказа (S23): позиции заказа группируются по товару и кладутся
 * в серверную корзину (PUT заменяет количество строки, поэтому суммируем).
 */
export async function addOrderItemsToCart(items: Order["items"]) {
  const quantities = new Map<string, number>();
  for (const item of items) {
    quantities.set(item.productId, (quantities.get(item.productId) ?? 0) + item.quantity);
  }
  for (const [productId, quantity] of quantities) {
    await apiRequest(`/cart/items/${encodeURIComponent(productId)}`, {
      method: "PUT",
      body: { quantity },
    });
  }
}

// ----- Компании менеджера (для заказа от имени клиента) -----------------------

export interface ManagerCompany {
  id: string;
  name: string;
  unp?: string | null;
}

export function listManagerCompanies() {
  return apiListRequest<ManagerCompany>("/manager/companies?page=0&size=100");
}
