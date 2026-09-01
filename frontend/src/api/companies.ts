import { apiListRequest, apiRequest } from "./client";
import {
  createMockCompany,
  deleteMockCompany,
  getMockCompany,
  isMockEnabled,
  listMockCompanies,
  updateMockCompany,
} from "./adminMocks";
import type {
  Company,
  CompanyFilters,
  CreateCompanyRequest,
  PagedResponse,
  UpdateCompanyRequest,
} from "../types/admin";
import { useAuthStore } from "../store/authStore";

function buildQuery(filters: Record<string, unknown>): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === "" || value === null) return;
    params.set(key, String(value));
  });
  const query = params.toString();
  return query ? `?${query}` : "";
}

export function listCompanies(filters: CompanyFilters): Promise<PagedResponse<Company>> {
  if (isMockEnabled()) return Promise.resolve(listMockCompanies(filters));
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiListRequest<Company>(`/admin/companies${buildQuery(filters as Record<string, unknown>)}`, {
    method: "GET",
    token,
  });
}

export function getCompany(id: string): Promise<Company> {
  if (isMockEnabled()) {
    const company = getMockCompany(id);
    if (!company) return Promise.reject({ code: "NOT_FOUND", message: "Компания не найдена" });
    return Promise.resolve(company);
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<Company>(`/admin/companies/${id}`, { method: "GET", token });
}

export function createCompany(body: CreateCompanyRequest): Promise<Company> {
  if (isMockEnabled()) return Promise.resolve(createMockCompany(body));
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<Company>("/admin/companies", { method: "POST", token, body });
}

export function updateCompany(id: string, body: UpdateCompanyRequest): Promise<Company> {
  if (isMockEnabled()) {
    try {
      return Promise.resolve(updateMockCompany(id, body));
    } catch {
      return Promise.reject({ code: "NOT_FOUND", message: "Компания не найдена" });
    }
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<Company>(`/admin/companies/${id}`, { method: "PUT", token, body });
}

export function deleteCompany(id: string): Promise<void> {
  if (isMockEnabled()) {
    try {
      deleteMockCompany(id);
      return Promise.resolve();
    } catch {
      return Promise.reject({ code: "NOT_FOUND", message: "Компания не найдена" });
    }
  }
  const token = useAuthStore.getState().accessToken ?? undefined;
  return apiRequest<void>(`/admin/companies/${id}`, { method: "DELETE", token });
}
