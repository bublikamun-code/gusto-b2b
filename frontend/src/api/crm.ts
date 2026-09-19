import { apiListRequest, apiRequest } from "./client";

// ----- типы (S29 контракты) ---------------------------------------------------

export type LeadStatus = "NEW" | "IN_PROGRESS" | "QUALIFIED" | "WON" | "LOST";

export interface Lead {
  id: string;
  source?: string | null;
  name: string;
  phone?: string | null;
  email?: string | null;
  companyName?: string | null;
  message?: string | null;
  status: LeadStatus;
  assignedManagerId?: string | null;
  createdAt: string;
}

export interface CrmTask {
  id: string;
  assigneeId: string;
  companyId?: string | null;
  title: string;
  description?: string | null;
  dueDate?: string | null;
  status: "OPEN" | "DONE" | "CANCELLED";
  overdue: boolean;
  createdAt: string;
}

export interface CrmNote {
  id: string;
  companyId: string;
  authorId: string;
  authorName?: string | null;
  body: string;
  createdAt: string;
}

export interface CrmDashboard {
  from: string;
  to: string;
  revenue: number;
  completedOrders: number;
  topProducts: Array<{ name: string; quantity: number; total: number }>;
  topCustomers: Array<{ name: string; orders: number; total: number }>;
  debt: number;
  leadsTotal: number;
  leadsWon: number;
  leadConversionPercent: number;
}

/** Зеркало воронки 2.8 для UI; источник истины — бэкенд (409). */
export const LEAD_TRANSITIONS: Record<LeadStatus, LeadStatus[]> = {
  NEW: ["IN_PROGRESS", "LOST"],
  IN_PROGRESS: ["QUALIFIED", "LOST"],
  QUALIFIED: ["WON", "LOST"],
  WON: [],
  LOST: [],
};

export const LEAD_STATUSES: LeadStatus[] = ["NEW", "IN_PROGRESS", "QUALIFIED", "WON", "LOST"];

export function leadStatusLabel(status: LeadStatus): string {
  switch (status) {
    case "NEW":
      return "Новый";
    case "IN_PROGRESS":
      return "В работе";
    case "QUALIFIED":
      return "Квалифицирован";
    case "WON":
      return "Успешно";
    case "LOST":
      return "Потерян";
  }
}

// ----- лиды -------------------------------------------------------------------

export function createLead(body: {
  name: string;
  phone?: string;
  companyName?: string;
  message?: string;
  source?: string;
}) {
  return apiRequest<Lead>("/crm/leads", { method: "POST", body });
}

export function listLeads(
  filters: {
    scope?: "mine" | "unassigned" | "pool" | "all";
    status?: LeadStatus;
    page?: number;
    size?: number;
  } = {},
) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => {
    if (v !== undefined && v !== null) params.set(k, String(v));
  });
  const query = params.toString();
  return apiListRequest<Lead>(`/crm/leads${query ? `?${query}` : ""}`);
}

export function assignLead(id: string, managerId: string) {
  return apiRequest<Lead>(`/crm/leads/${id}/assign`, { method: "POST", body: { managerId } });
}

export function changeLeadStatus(id: string, status: LeadStatus) {
  return apiRequest<Lead>(`/crm/leads/${id}/status`, { method: "POST", body: { status } });
}

// ----- задачи -----------------------------------------------------------------

export function listTasks(scope: "mine" | "overdue" | "all" = "mine") {
  return apiListRequest<CrmTask>(`/crm/tasks?scope=${scope}&size=50`);
}

export function changeTaskStatus(id: string, status: "DONE" | "CANCELLED") {
  return apiRequest<CrmTask>(`/crm/tasks/${id}/status?status=${status}`, { method: "POST" });
}

// ----- заметки ----------------------------------------------------------------

export function listNotes(companyId: string) {
  return apiListRequest<CrmNote>(`/crm/notes?companyId=${companyId}`);
}

export function addNote(companyId: string, body: string) {
  return apiRequest<CrmNote>("/crm/notes", { method: "POST", body: { companyId, body } });
}

// ----- дашборд ----------------------------------------------------------------

export function getDashboard(from?: string, to?: string) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const query = params.toString();
  return apiRequest<CrmDashboard>(`/crm/dashboard${query ? `?${query}` : ""}`);
}
