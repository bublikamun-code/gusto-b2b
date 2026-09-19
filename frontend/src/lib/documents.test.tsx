import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  formatDocDate,
  invoiceStatusBadge,
  invoiceStatusLabel,
  waybillTypeLabel,
} from "./documents";

describe("documents labels (S27)", () => {
  it("подписывает статусы счёта", () => {
    expect(invoiceStatusLabel("DRAFT")).toBe("Черновик");
    expect(invoiceStatusLabel("ISSUED")).toBe("Выпущен");
    expect(invoiceStatusLabel("PARTIALLY_PAID")).toBe("Частично оплачен");
    expect(invoiceStatusLabel("PAID")).toBe("Оплачен");
    expect(invoiceStatusLabel("CANCELLED")).toBe("Отменён");
  });

  it("подписывает тип накладной", () => {
    expect(waybillTypeLabel("TTN")).toBe("ТТН");
    expect(waybillTypeLabel("TN")).toBe("ТН");
  });

  it("бейджи: выпущен — акцент, оплачен — успех", () => {
    render(
      <>
        {invoiceStatusBadge("ISSUED")}
        {invoiceStatusBadge("PAID")}
      </>,
    );
    // css-модули в vitest хэшируются — проверяем вхождение варианта в класс
    expect(screen.getByText("Выпущен").className).toMatch(/_accent_/);
    expect(screen.getByText("Оплачен").className).toMatch(/_success_/);
  });

  it("форматирует дату документа", () => {
    expect(formatDocDate("2026-08-24T00:00:00Z")).toMatch(/24\.08\.2026/);
  });
});
