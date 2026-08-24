import type { components } from "../api/schema";

export type User = components["schemas"]["User"];
export type Company = components["schemas"]["Company"];
export type Role = components["schemas"]["Role"];
export type CompanyStatus = components["schemas"]["CompanyStatus"];

export type CreateUserRequest = components["schemas"]["CreateUserRequest"];
export type UpdateUserRequest = components["schemas"]["UpdateUserRequest"];
export type CreateCompanyRequest = components["schemas"]["CreateCompanyRequest"];
export type UpdateCompanyRequest = components["schemas"]["UpdateCompanyRequest"];

export interface UserFilters {
  page?: number;
  size?: number;
  role?: Role | "";
  companyId?: string;
  managerId?: string;
  search?: string;
  isActive?: boolean | "";
}

export interface CompanyFilters {
  page?: number;
  size?: number;
  status?: CompanyStatus | "";
  managerId?: string;
  search?: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ResetPasswordResult {
  temporaryPassword: string;
}
