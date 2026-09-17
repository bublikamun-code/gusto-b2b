import { describe, expect, it } from "vitest";
import { formatMoney } from "./format";

describe("formatMoney", () => {
  it("always renders two decimals", () => {
    expect(formatMoney(12)).toMatch(/12,00/);
    expect(formatMoney(0)).toMatch(/0,00/);
    expect(formatMoney(7.5)).toMatch(/7,50/);
  });

  it("groups thousands with a space-like separator", () => {
    expect(formatMoney(1234.5)).toMatch(/1.?234,50/);
  });

  it("formats in BYN currency", () => {
    const formatted = formatMoney(10);
    expect(formatted).toMatch(/BYN|Br/i);
  });
});
