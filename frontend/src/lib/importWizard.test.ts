import { describe, expect, it } from "vitest";
import { importResultMessage, previewIsClean } from "./importWizard";
import type { ImportPreview, ImportReport } from "../api/adminOperations";

describe("previewIsClean", () => {
  it("true when no errors", () => {
    const preview: ImportPreview = {
      type: "PRICES",
      rowsTotal: 2,
      errorsCount: 0,
      rows: [],
    };
    expect(previewIsClean(preview)).toBe(true);
  });

  it("false when errors present", () => {
    const preview: ImportPreview = {
      type: "STOCK",
      rowsTotal: 3,
      errorsCount: 1,
      rows: [],
    };
    expect(previewIsClean(preview)).toBe(false);
  });
});

describe("importResultMessage", () => {
  const report = (rowsOk: number, rowsError: number): ImportReport => ({
    integrationFileId: "00000000-0000-0000-0000-000000000001",
    type: "PRICES",
    status: "DONE",
    rowsTotal: rowsOk + rowsError,
    rowsOk,
    rowsError,
    errors: [],
  });

  it("reports success without errors", () => {
    expect(importResultMessage(report(5, 0))).toBe(
      "Импорт выполнен: все 5 строк без ошибок",
    );
  });

  it("reports partial errors", () => {
    expect(importResultMessage(report(3, 2))).toBe("Импорт выполнен: OK 3, ошибок 2");
  });
});
