import { describe, expect, it } from "vitest";
import { locationFieldsForType, validateDocumentLocations } from "./warehouse";
import type { WarehouseDocumentType } from "../api/warehouse";

const MAIN = "11111111-1111-1111-1111-111111111111";
const RESERVE = "22222222-2222-2222-2222-222222222222";

describe("validateDocumentLocations", () => {
  const cases: Array<[WarehouseDocumentType, Parameters<typeof validateDocumentLocations>[1], string | null]> = [
    ["INCOMING", { locationToId: RESERVE }, null],
    ["INCOMING", {}, "Выберите склад приёмки"],
    ["OUTGOING", { locationFromId: MAIN }, null],
    ["OUTGOING", {}, "Выберите склад"],
    ["WRITE_OFF", {}, "Выберите склад"],
    ["INVENTORY", { locationFromId: MAIN }, null],
    ["INVENTORY", {}, "Выберите склад"],
    ["TRANSFER", { locationFromId: MAIN, locationToId: RESERVE }, null],
    ["TRANSFER", { locationFromId: MAIN }, "Выберите оба склада"],
    ["TRANSFER", { locationFromId: MAIN, locationToId: MAIN }, "Склады должны различаться"],
  ];

  it.each(cases)("type %s with %j → %s", (type, ids, expected) => {
    expect(validateDocumentLocations(type, ids)).toBe(expected);
  });
});

describe("locationFieldsForType", () => {
  it("incoming shows only target", () => {
    expect(locationFieldsForType("INCOMING")).toEqual({
      from: false,
      to: true,
      fromLabel: "",
    });
  });

  it("transfer shows both", () => {
    const fields = locationFieldsForType("TRANSFER");
    expect(fields.from).toBe(true);
    expect(fields.to).toBe(true);
  });

  it("inventory counts on the source location", () => {
    const fields = locationFieldsForType("INVENTORY");
    expect(fields.from).toBe(true);
    expect(fields.to).toBe(false);
  });
});
