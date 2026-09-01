import { apiListRequest, apiRequest } from "./client";
import type { components } from "./schema";

export type CatalogCategory = components["schemas"]["Category"];
export type CatalogBrand = components["schemas"]["Brand"];
export type CatalogProduct = components["schemas"]["CatalogProduct"];

export interface ProductFilters {
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

export function listCategories(): Promise<CatalogCategory[]> {
  return apiRequest<CatalogCategory[]>("/catalog/categories");
}

export function listBrands(): Promise<CatalogBrand[]> {
  return apiRequest<CatalogBrand[]>("/catalog/brands");
}

export function listProducts(filters: ProductFilters) {
  return apiListRequest<CatalogProduct>(`/catalog/products${buildQuery(filters as Record<string, unknown>)}`);
}

export function getProduct(sku: string): Promise<CatalogProduct> {
  return apiRequest<CatalogProduct>(`/catalog/products/${encodeURIComponent(sku)}`);
}
