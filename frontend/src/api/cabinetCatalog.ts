import { apiListRequest } from "./client";
import type { components } from "./schema";

export type CabinetProduct = components["schemas"]["CabinetProduct"];

export interface CabinetProductFilters {
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

export function listCabinetProducts(filters: CabinetProductFilters = {}) {
  return apiListRequest<CabinetProduct>(`/cabinet/catalog${buildQuery(filters as Record<string, unknown>)}`);
}
