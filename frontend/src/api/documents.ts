import { apiListRequest, apiRequest } from "./client";
import { useAuthStore } from "../store/authStore";

// ----- типы (S24/S26 контракты) ----------------------------------------------

export type InvoiceStatus =
  | "DRAFT"
  | "ISSUED"
  | "PARTIALLY_PAID"
  | "PAID"
  | "CANCELLED";

export type WaybillType = "TN" | "TTN";

export interface Invoice {
  id: string;
  displayNumber: string;
  number: string;
  issueDate: string;
  orderId: string;
  customerCompanyId?: string | null;
  totalAmount: number;
  totalVat: number;
  status: InvoiceStatus;
  createdAt: string;
  items: Array<{
    sku?: string | null;
    productName?: string | null;
    unit?: string | null;
    quantity: number;
    unitPrice: number;
    vatRate: number;
    total: number;
  }>;
}

export interface Waybill {
  id: string;
  type: WaybillType;
  displayNumber: string;
  number: string;
  series: string;
  issueDate: string;
  orderId: string;
  totalAmount: number;
  totalVat: number;
  totalWeight: number;
  createdAt: string;
  items: Array<{
    productName?: string | null;
    quantity: number;
    unitPrice: number;
    total: number;
    weight?: number | null;
  }>;
}

export interface CreateWaybillRequest {
  orderId: string;
  type: WaybillType;
  invoiceId?: string;
  consigneeName?: string;
  consigneeAddress?: string;
  vehicle?: string;
  driver?: string;
  carrierCompany?: string;
}

// ----- бэк-офис --------------------------------------------------------------

export function listInvoices(page = 0, size = 50) {
  return apiListRequest<Invoice>(`/invoices?page=${page}&size=${size}`);
}

export function createInvoice(orderId: string) {
  return apiRequest<Invoice>("/invoices", { method: "POST", body: { orderId } });
}

export function issueInvoice(id: string) {
  return apiRequest<Invoice>(`/invoices/${encodeURIComponent(id)}/issue`, { method: "POST" });
}

export function listWaybills(page = 0, size = 50) {
  return apiListRequest<Waybill>(`/waybills?page=${page}&size=${size}`);
}

export function createWaybill(body: CreateWaybillRequest) {
  return apiRequest<Waybill>("/waybills", { method: "POST", body });
}

// ----- кабинет юрлица ---------------------------------------------------------

export function listCabinetInvoices(page = 0, size = 50) {
  return apiListRequest<Invoice>(`/cabinet/invoices?page=${page}&size=${size}`);
}

export function listCabinetWaybills(page = 0, size = 50) {
  return apiListRequest<Waybill>(`/cabinet/waybills?page=${page}&size=${size}`);
}

// ----- скачивание PDF ----------------------------------------------------------

/**
 * Скачивание PDF документа (S25/S26): PRIVATE-файл отдаётся только с токеном,
 * сохраняем через blob (Content-Disposition attachment у бэкенда).
 */
export async function downloadPdf(path: string, filename: string) {
  const token = useAuthStore.getState().accessToken;
  const response = await fetch(`/api/v1${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Не удалось скачать PDF");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
