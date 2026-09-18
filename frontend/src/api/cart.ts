import { apiRequest } from "./client";

export interface CartLine {
  productId: string;
  sku: string;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

export interface ServerCart {
  items: CartLine[];
  totalAmount: number;
  totalVat: number;
}

export interface CreateOrderRequest {
  customerCompanyId?: string;
  items?: Array<{ productId: string; quantity: number }>;
  deliveryType: "PICKUP" | "DELIVERY";
  deliveryAddress?: string;
  recipientName?: string;
  recipientPhone?: string;
  note?: string;
}

export interface Order {
  id: string;
  number: string;
  status: string;
  customerCompanyId?: string | null;
  managerId?: string | null;
  deliveryType: "PICKUP" | "DELIVERY";
  deliveryAddress?: string | null;
  recipientName?: string | null;
  recipientPhone?: string | null;
  note?: string | null;
  totalAmount: number;
  totalVat: number;
  createdAt: string;
  items: Array<{
    productId: string;
    sku?: string | null;
    productName?: string | null;
    unit?: string | null;
    quantity: number;
    unitPrice: number;
    total: number;
  }>;
}

export const getCart = () => apiRequest<ServerCart>("/cart");

export const putCartItem = (productId: string, quantity: number) =>
  apiRequest<ServerCart>(`/cart/items/${encodeURIComponent(productId)}`, {
    method: "PUT",
    body: { quantity },
  });

export const clearCart = () => apiRequest<ServerCart>("/cart", { method: "DELETE" });

export const createOrder = (body: CreateOrderRequest, idempotencyKey: string) =>
  apiRequest<Order>("/orders", { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } });
