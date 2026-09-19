import { describe, expect, it } from "vitest";
import {
  DEFAULT_DELIVERY_STEPS,
  DEFAULT_DELIVERY_TITLE,
  resolveLanding,
  type PublicLanding,
} from "./landingTexts";

describe("resolveLanding", () => {
  it("uses defaults when settings are empty", () => {
    const result = resolveLanding(null);
    expect(result.hero.eyebrow).toBe("Интернет-магазин · Минск");
    expect(result.deliveryTitle).toBe(DEFAULT_DELIVERY_TITLE);
    expect(result.deliverySteps).toEqual(DEFAULT_DELIVERY_STEPS);
  });

  it("uses defaults when backend returned empty objects", () => {
    const result = resolveLanding({ hero: {}, delivery: {} });
    expect(result.hero.title).toContain("Свежая поставка");
    expect(result.deliverySteps).toHaveLength(3);
  });

  it("merges partial hero override", () => {
    const result = resolveLanding({
      hero: { eyebrow: "Новинки каждый день" },
      delivery: {},
    });
    expect(result.hero.eyebrow).toBe("Новинки каждый день");
    expect(result.hero.title).toContain("Свежая поставка");
  });

  it("ignores blank strings", () => {
    const result = resolveLanding({
      hero: { title: "   " },
      delivery: { title: "" },
    });
    expect(result.hero.title).toContain("Свежая поставка");
    expect(result.deliveryTitle).toBe(DEFAULT_DELIVERY_TITLE);
  });

  it("prefers custom delivery steps", () => {
    const steps = [{ number: "1", title: "Один", text: "Раз" }];
    const result = resolveLanding({ hero: {}, delivery: { steps } });
    expect(result.deliverySteps).toEqual(steps);
  });

  it("handles undefined landing like null", () => {
    const result = resolveLanding(undefined);
    expect(result.deliveryTitle).toBe(DEFAULT_DELIVERY_TITLE);
  });

  it("accepts a full landing payload", () => {
    const landing: PublicLanding = {
      hero: { eyebrow: "A", title: "B", text: "C" },
      delivery: { title: "Доставка", steps: [{ number: "1", title: "T", text: "X" }] },
    };
    const result = resolveLanding(landing);
    expect(result.hero).toEqual({ eyebrow: "A", title: "B", text: "C" });
    expect(result.deliveryTitle).toBe("Доставка");
    expect(result.deliverySteps).toEqual([{ number: "1", title: "T", text: "X" }]);
  });
});
