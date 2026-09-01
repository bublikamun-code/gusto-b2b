import { apiListRequest, apiRequest } from "./client";
import type { components } from "./schema";

export type AdminProduct = components["schemas"]["Product"];
export type AdminProductRequest = components["schemas"]["ProductRequest"];

export interface AdminProductFilters {
  page?: number;
  size?: number;
  search?: string;
  categoryId?: string;
  brandId?: string;
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

export function listAdminProducts(filters: AdminProductFilters = {}) {
  return apiListRequest<AdminProduct>(`/admin/catalog/products${buildQuery(filters as Record<string, unknown>)}`);
}

export function getAdminProduct(id: string): Promise<AdminProduct> {
  return apiRequest<AdminProduct>(`/admin/catalog/products/${encodeURIComponent(id)}`);
}

export function createAdminProduct(body: AdminProductRequest): Promise<AdminProduct> {
  return apiRequest<AdminProduct>("/admin/catalog/products", { method: "POST", body });
}

export function updateAdminProduct(id: string, body: AdminProductRequest): Promise<AdminProduct> {
  return apiRequest<AdminProduct>(`/admin/catalog/products/${encodeURIComponent(id)}`, {
    method: "PUT",
    body,
  });
}

export function deleteAdminProduct(id: string): Promise<void> {
  return apiRequest<void>(`/admin/catalog/products/${encodeURIComponent(id)}`, { method: "DELETE" });
}
