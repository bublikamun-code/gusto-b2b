import { describe, expect, it } from "vitest";
import { roundToStep, stepCount } from "./units";

describe("roundToStep", () => {
  it("rounds to nearest step", () => {
    expect(roundToStep(0.7, 0.5)).toBeCloseTo(0.5, 10);
    expect(roundToStep(0.3, 0.5)).toBeCloseTo(0.5, 10);
    expect(roundToStep(1.4, 0.5)).toBeCloseTo(1.5, 10);
  });

  it("keeps exact multiples untouched", () => {
    expect(roundToStep(2, 0.5)).toBe(2);
    expect(roundToStep(0.5, 0.5)).toBe(0.5);
  });

  it("clamps negatives to zero", () => {
    expect(roundToStep(-1, 0.5)).toBe(0);
  });

  it("no step means no rounding", () => {
    expect(roundToStep(1.23, undefined)).toBe(1.23);
    expect(roundToStep(1.23, null)).toBe(1.23);
    expect(roundToStep(1.23, 0)).toBe(1.23);
    expect(roundToStep(1.23, Number.NaN)).toBe(1.23);
  });

  it("integer step for штучный товар", () => {
    expect(roundToStep(2.6, 1)).toBe(3);
    expect(roundToStep(2.4, 1)).toBe(2);
  });
});

describe("stepCount", () => {
  it("counts whole steps", () => {
    expect(stepCount(1.5, 0.5)).toBe(3);
    expect(stepCount(2, 1)).toBe(2);
  });

  it("returns null without a step", () => {
    expect(stepCount(1.5, undefined)).toBeNull();
  });
});
