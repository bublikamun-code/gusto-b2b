import { describe, expect, it } from "vitest";
import { parseQuantityInput } from "./quantityInput";

describe("parseQuantityInput", () => {
  it("empty draft is not sent anywhere", () => {
    expect(parseQuantityInput("")).toEqual({ kind: "empty" });
    expect(parseQuantityInput("   ")).toEqual({ kind: "empty" });
  });

  it("garbage is invalid", () => {
    expect(parseQuantityInput("abc")).toEqual({ kind: "invalid" });
    expect(parseQuantityInput("1.2.3")).toEqual({ kind: "invalid" });
    expect(parseQuantityInput("-3")).toEqual({ kind: "invalid" });
  });

  it("zero means removal attempt, routed to the Убрать button", () => {
    expect(parseQuantityInput("0")).toEqual({ kind: "zero" });
    expect(parseQuantityInput("0.000")).toEqual({ kind: "zero" });
  });

  it("positive weight quantities pass through", () => {
    expect(parseQuantityInput("12.5")).toEqual({ kind: "ok", value: 12.5 });
    expect(parseQuantityInput("0.001")).toEqual({ kind: "ok", value: 0.001 });
    expect(parseQuantityInput(" 12.5 ")).toEqual({ kind: "ok", value: 12.5 });
  });

  it("comma decimal separator is accepted", () => {
    expect(parseQuantityInput("12,5")).toEqual({ kind: "ok", value: 12.5 });
    expect(parseQuantityInput("0,001")).toEqual({ kind: "ok", value: 0.001 });
  });
});
