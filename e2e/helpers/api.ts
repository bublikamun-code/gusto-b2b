/**
 * API-хелперы E2E (S39): прямые вызовы backend (:8080) для подготовки данных,
 * которые не имеют UI (временный пароль, регистрация платежа), и для k6-подготовки.
 * Бизнес-действия в тестах идут через UI, как у пользователя.
 */

const API = process.env.E2E_API_URL ?? "http://localhost:8080/api/v1";

export const ADMIN = { email: "admin@gustomeat.by", password: "change-me" };

interface Envelope<T> {
  data: T;
  error?: { code: string; message: string };
}

export async function apiCall<T>(
  method: string,
  path: string,
  options: { token?: string; body?: unknown; idempotencyKey?: string } = {},
): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;
  const response = await fetch(`${API}${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const text = await response.text();
  const envelope = text ? (JSON.parse(text) as Envelope<T>) : ({ data: null } as Envelope<T>);
  if (!response.ok || envelope.error) {
    throw new Error(`${method} ${path} → ${response.status}: ${envelope.error?.message ?? text}`);
  }
  return envelope.data as T;
}

export function apiLogin(email: string, password: string): Promise<string> {
  return apiCall<{ accessToken: string }>("POST", "/auth/login", {
    body: { email, password },
  }).then((r) => r.accessToken);
}

export interface Company {
  id: string;
  name: string;
  unp: string;
}

export function createCompany(token: string, name: string, unp: string): Promise<Company> {
  return apiCall<Company>("POST", "/admin/companies", {
    token,
    body: { name, unp, legalAddress: "г. Минск, ул. E2E, 1", status: "ACTIVE" },
  });
}

export function createUser(
  token: string,
  body: { email: string; fullName: string; role: string; companyId?: string },
): Promise<{ id: string; email: string }> {
  return apiCall("POST", "/admin/users", { token, body });
}

export function resetPassword(token: string, userId: string): Promise<string> {
  return apiCall<{ temporaryPassword: string }>(
    "POST",
    `/admin/users/${userId}/reset-password`,
    { token, body: {} },
  ).then((r) => r.temporaryPassword);
}

export function findUserByEmail(token: string, email: string): Promise<{ id: string } | null> {
  // пагинированный ответ: data — сразу массив
  return apiCall<{ id: string; email: string }[]>(
    "GET",
    `/admin/users?search=${encodeURIComponent(email)}&size=50`,
    { token },
  ).then((items) => (items ?? []).find((u) => u.email === email) ?? null);
}

export function registerPayment(
  token: string,
  invoiceId: string,
  amount: number,
): Promise<unknown> {
  return apiCall("POST", `/invoices/${invoiceId}/payments`, {
    token,
    body: { amount, method: "CASH", note: "E2E оплата" },
  });
}

export interface CatalogProduct {
  id: string;
  sku: string;
  name: string;
}

export function listCatalogProducts(page = 0, size = 5): Promise<CatalogProduct[]> {
  return apiCall<{ items: CatalogProduct[] }>(
    "GET",
    `/catalog/products?page=${page}&size=${size}`,
  ).then((p) => p.items);
}

export function listAdminProducts(token: string): Promise<CatalogProduct[]> {
  return apiCall<{ items: CatalogProduct[] }>("GET", "/admin/catalog/products?size=5", {
    token,
  }).then((p) => p.items);
}

export function listInvoices(token: string) {
  // пагинированный ответ: data — сразу массив
  return apiCall<
    { id: string; number: string; status: string; totalAmount: number }[]
  >("GET", "/invoices?page=0&size=50", { token });
}

export function listOrders(token: string) {
  return apiCall<{
    items: { id: string; number: string; status: string }[];
  }>("GET", "/orders?page=0&size=50", { token });
}
