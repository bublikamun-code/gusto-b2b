import type { ReactNode } from "react";
import { Badge } from "../components/ui";
import type { InvoiceStatus, WaybillType } from "../api/documents";

/** Подписи и бейджи статуса счёта (S24; PARTIALLY_PAID/PAID — с S28). */
export function invoiceStatusLabel(status: InvoiceStatus | undefined): string {
  switch (status) {
    case "DRAFT":
      return "Черновик";
    case "ISSUED":
      return "Выпущен";
    case "PARTIALLY_PAID":
      return "Частично оплачен";
    case "PAID":
      return "Оплачен";
    case "CANCELLED":
      return "Отменён";
    default:
      return status ?? "—";
  }
}

export function invoiceStatusBadge(status: InvoiceStatus | undefined): ReactNode {
  switch (status) {
    case "ISSUED":
      return <Badge variant="accent">{invoiceStatusLabel(status)}</Badge>;
    case "PAID":
      return <Badge variant="success">{invoiceStatusLabel(status)}</Badge>;
    case "CANCELLED":
      return <Badge variant="outline">{invoiceStatusLabel(status)}</Badge>;
    case "PARTIALLY_PAID":
      return <Badge variant="warning">{invoiceStatusLabel(status)}</Badge>;
    default:
      return <Badge variant="neutral">{invoiceStatusLabel(status)}</Badge>;
  }
}

export function waybillTypeLabel(type: WaybillType | undefined): string {
  switch (type) {
    case "TTN":
      return "ТТН";
    case "TN":
      return "ТН";
    default:
      return type ?? "—";
  }
}

export function formatDocDate(iso: string): string {
  return new Date(iso).toLocaleDateString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}
