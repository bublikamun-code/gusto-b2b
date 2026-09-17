import type { WarehouseDocumentType } from "../api/warehouse";

export const DOCUMENT_TYPE_LABELS: Record<WarehouseDocumentType, string> = {
  INCOMING: "Приход",
  OUTGOING: "Расход",
  TRANSFER: "Перемещение",
  WRITE_OFF: "Списание",
  INVENTORY: "Инвентаризация",
};

export const DOCUMENT_TYPE_OPTIONS = Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const DOCUMENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Черновик",
  CONFIRMED: "Проведён",
  CANCELLED: "Отменён",
};

export const PURCHASE_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Черновик",
  SENT: "Отправлен",
  PARTIAL: "Частично принят",
  RECEIVED: "Принят",
  CANCELLED: "Отменён",
};

export interface DocumentLocationIds {
  locationFromId?: string | null;
  locationToId?: string | null;
}

/**
 * Правила складов по типу документа (S18.1/S18.3, зеркало бэкенда):
 *  - приход: locationTo; расход/списание: locationFrom; инвентаризация: locationFrom;
 *  - перемещение: оба, и они различны.
 * Возвращает строку ошибки или null.
 */
export function validateDocumentLocations(
  type: WarehouseDocumentType,
  ids: DocumentLocationIds,
): string | null {
  switch (type) {
    case "INCOMING":
      return ids.locationToId ? null : "Выберите склад приёмки";
    case "OUTGOING":
    case "WRITE_OFF":
    case "INVENTORY":
      return ids.locationFromId ? null : "Выберите склад";
    case "TRANSFER":
      if (!ids.locationFromId || !ids.locationToId) {
        return "Выберите оба склада";
      }
      return ids.locationFromId !== ids.locationToId ? null : "Склады должны различаться";
    default:
      return "Неизвестный тип документа";
  }
}

/** Какие поля складов показывать в форме для типа документа. */
export function locationFieldsForType(type: WarehouseDocumentType): {
  from: boolean;
  to: boolean;
  fromLabel: string;
} {
  switch (type) {
    case "INCOMING":
      return { from: false, to: true, fromLabel: "" };
    case "TRANSFER":
      return { from: true, to: true, fromLabel: "Со склада" };
    case "OUTGOING":
    case "WRITE_OFF":
      return { from: true, to: false, fromLabel: "Со склада" };
    case "INVENTORY":
      return { from: true, to: false, fromLabel: "Инвентаризуемый склад" };
    default:
      return { from: false, to: false, fromLabel: "" };
  }
}
