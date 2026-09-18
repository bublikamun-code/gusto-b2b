import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  allowedNextStatuses,
  orderStatusLabel,
  orderStatusBadge,
} from "./orderStatus";

describe("orderStatus (S23, зеркало статус-машины S22)", () => {
  it("подписывает все статусы по-русски", () => {
    expect(orderStatusLabel("NEW")).toBe("Новый");
    expect(orderStatusLabel("CONFIRMED")).toBe("Подтверждён");
    expect(orderStatusLabel("PROCESSING")).toBe("В сборке");
    expect(orderStatusLabel("READY")).toBe("Готов к выдаче");
    expect(orderStatusLabel("SHIPPED")).toBe("Отгружен");
    expect(orderStatusLabel("COMPLETED")).toBe("Выполнен");
    expect(orderStatusLabel("CANCELLED")).toBe("Отменён");
  });

  it("допустимые переходы совпадают с бэкенд-машиной", () => {
    expect(allowedNextStatuses("NEW")).toEqual(["CONFIRMED", "CANCELLED"]);
    expect(allowedNextStatuses("SHIPPED")).toEqual(["COMPLETED"]);
    // терминальные
    expect(allowedNextStatuses("COMPLETED")).toEqual([]);
    expect(allowedNextStatuses("CANCELLED")).toEqual([]);
    // из любых стадий до отгрузки можно отменить; после отгрузки — только вперёд
    expect(allowedNextStatuses("CONFIRMED")).toContain("CANCELLED");
    expect(allowedNextStatuses("PROCESSING")).toContain("CANCELLED");
    expect(allowedNextStatuses("READY")).toContain("CANCELLED");
    expect(allowedNextStatuses("SHIPPED")).not.toContain("CANCELLED");
  });

  it("бейджи: новый — акцент, выполнен — успех, отменён — контур", () => {
    render(
      <>
        {orderStatusBadge("NEW")}
        {orderStatusBadge("COMPLETED")}
        {orderStatusBadge("CANCELLED")}
      </>,
    );
    // css-модули в vitest хэшируются — проверяем вхождение варианта в класс
    expect(screen.getByText("Новый").className).toMatch(/_accent_/);
    expect(screen.getByText("Выполнен").className).toMatch(/_success_/);
    expect(screen.getByText("Отменён").className).toMatch(/_outline_/);
  });
});
