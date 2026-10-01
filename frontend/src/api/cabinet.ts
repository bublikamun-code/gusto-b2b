import { apiRequest } from "./client";

/** Сводка кабинета для дашборда: заказы в работе, расчёты, последний заказ. */
export interface CabinetSummary {
  companyName: string | null;
  activeOrdersCount: number;
  activeOrdersTotal: number;
  awaitingConfirmationCount: number;
  unpaidInvoicesCount: number;
  outstandingDebt: number;
  lastOrderNumber: string | null;
  lastOrderStatus: string | null;
  lastOrderAt: string | null;
}

export function getCabinetSummary() {
  return apiRequest<CabinetSummary>("/cabinet/summary");
}