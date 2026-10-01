import { apiListRequest, apiRequest } from "./client";
import { useAuthStore } from "../store/authStore";
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

/**
 * Правила скидок компании клиента (матрица 2.1: «Прайс/скидки в кабинете»).
 * Сами цены приходят из /cabinet/catalog — там customerPrice уже посчитан по 2.5.
 */
export interface ClientDiscount {
  id: string;
  brandName: string | null;
  categoryName: string | null;
  discountPercent: number;
  validFrom: string;
  validTo: string | null;
  active: boolean;
}

export function listClientDiscounts() {
  return apiRequest<ClientDiscount[]>("/cabinet/pricing/discounts");
}

/** Выгрузка прайса в .xlsx для 1С (S36). */
export async function downloadPricingXlsx() {
  const token = useAuthStore.getState().accessToken;
  const response = await fetch("/api/v1/cabinet/pricing/export", {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Не удалось выгрузить прайс");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "pricing.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
