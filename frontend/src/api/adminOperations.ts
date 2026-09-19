import { apiListRequest, apiRequest } from "./client";
import { useAuthStore } from "../store/authStore";

/**
 * Операционный центр (S38): настройки, журнал аудита, дашборд процессов,
 * мастер импорта/экспорта 1С (эндпоинты S35/S36).
 */

// ----- настройки ---------------------------------------------------------------

export interface SellerSettings {
  name: string;
  unp: string;
  address: string;
  bankAccount: string;
  bankName: string;
  bankBic: string;
}

export interface DocumentSettings {
  seriesTtn: string;
  seriesTn: string;
  vatDefault: number;
}

export interface NotificationSettings {
  /** write-only: null — не менять, "" — убрать токен из settings, иначе записать */
  telegramBotToken: string | null;
  telegramTokenSet: boolean;
  telegramTokenSource: "settings" | "env" | "none";
  rules: Record<string, boolean>;
}

export interface StockSettings {
  defaultLocationId: string;
}

export interface LocationOption {
  id: string;
  name: string;
}

export interface AuthSettings {
  requireEmailConfirmation: boolean;
}

export interface HeroSettings {
  eyebrow: string;
  title: string;
  text: string;
}

export interface DeliveryStep {
  number: string;
  title: string;
  text: string;
}

export interface DeliverySettings {
  title: string;
  steps: DeliveryStep[];
}

export interface LandingSettings {
  hero: HeroSettings | null;
  delivery: DeliverySettings | null;
}

export interface SettingsResponse {
  seller: SellerSettings;
  documents: DocumentSettings;
  notifications: NotificationSettings;
  stock: StockSettings;
  auth: AuthSettings;
  landing: LandingSettings | null;
  locations: LocationOption[];
}

export function getSettings() {
  return apiRequest<SettingsResponse>("/admin/settings");
}

export function updateSellerSettings(body: Partial<SellerSettings>) {
  return apiRequest<SettingsResponse>("/admin/settings/seller", { method: "PUT", body });
}

export function updateDocumentSettings(body: Partial<DocumentSettings>) {
  return apiRequest<SettingsResponse>("/admin/settings/documents", { method: "PUT", body });
}

export function updateNotificationSettings(body: {
  telegramBotToken?: string | null;
  rules?: Record<string, boolean>;
}) {
  return apiRequest<SettingsResponse>("/admin/settings/notifications", { method: "PUT", body });
}

export function updateStockSettings(body: Partial<StockSettings>) {
  return apiRequest<SettingsResponse>("/admin/settings/stock", { method: "PUT", body });
}

export function updateAuthSettings(body: Partial<AuthSettings>) {
  return apiRequest<SettingsResponse>("/admin/settings/auth", { method: "PUT", body });
}

export function updateLandingSettings(body: {
  hero?: HeroSettings;
  delivery?: { title: string; steps: DeliveryStep[] };
}) {
  return apiRequest<SettingsResponse>("/admin/settings/landing", { method: "PUT", body });
}

/** Публичные тексты лендинга: пустые объекты — показывать дефолты витрины. */
export function getPublicLanding() {
  return apiRequest<{
    hero: Partial<HeroSettings>;
    delivery: Partial<DeliverySettings>;
  }>("/cms/landing");
}

// ----- журнал аудита -------------------------------------------------------------

export interface AuditEntry {
  id: string;
  actorId: string;
  actorEmail: string | null;
  actorName: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  createdAt: string;
}

export interface AuditFilters {
  action?: string;
  targetType?: string;
  targetId?: string;
  actorId?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export function searchAudit(filters: AuditFilters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === "" || value === null) return;
    params.set(key, String(value));
  });
  const query = params.toString();
  return apiListRequest<AuditEntry>(`/admin/audit${query ? `?${query}` : ""}`);
}

// ----- дашборд процессов -----------------------------------------------------------

export interface OperationsDashboard {
  ordersToday: number;
  requestsToday: number;
  unpaidInvoices: { count: number; outstanding: number };
  lowStock: {
    productId: string;
    sku: string;
    productName: string;
    available: number;
    minStock: number;
  }[];
  overdueTasks: {
    id: string;
    title: string;
    assigneeName: string;
    dueDate: string;
  }[];
}

export function getOperationsDashboard() {
  return apiRequest<OperationsDashboard>("/admin/operations/dashboard");
}

// ----- мастер 1С (S35/S36) -----------------------------------------------------------

export interface ImportReport {
  integrationFileId: string;
  type: string;
  status: string;
  rowsTotal: number;
  rowsOk: number;
  rowsError: number;
  errors: { row: number; message: string }[];
}

export interface ImportPreview {
  type: string;
  rowsTotal: number;
  errorsCount: number;
  rows: { row: number; sku: string; value: number | null; ok: boolean; message: string | null }[];
}

/** Предпросмотр: файл разбирается и проверяется, ничего не записывается. */
export function previewImport(type: "prices" | "stock", file: File) {
  const body = new FormData();
  body.append("file", file);
  return apiRequest<ImportPreview>(`/admin/import/${type}/preview`, { method: "POST", body });
}

/** Применение импорта: прайсы (с архивацией отсутствующих) или остатки. */
export function applyImport(
  type: "prices" | "stock",
  file: File,
  options: { archiveMissing?: boolean } = {},
) {
  const body = new FormData();
  body.append("file", file);
  const params = new URLSearchParams();
  if (type === "prices" && options.archiveMissing) params.set("archiveMissing", "true");
  const query = params.toString();
  return apiRequest<ImportReport>(`/admin/import/${type}${query ? `?${query}` : ""}`, {
    method: "POST",
    body,
  });
}

/**
 * Выгрузка .xlsx для 1С за период (S36): заказы, счета, накладные.
 * Файл приходит attachment'ом — сохраняем через blob.
 */
export async function downloadExport(
  kind: "orders" | "invoices" | "waybills",
  from: string,
  to: string,
) {
  const token = useAuthStore.getState().accessToken;
  const response = await fetch(
    `/api/v1/admin/export/${kind}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      credentials: "include",
    },
  );
  if (!response.ok) {
    throw new Error("Не удалось скачать выгрузку");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${kind}-${from}_${to}.xlsx`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
