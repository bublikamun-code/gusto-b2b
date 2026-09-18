import { describe, expect, it } from "vitest";
import { applySeo } from "./seo";

describe("applySeo", () => {
  it("sets title, description and OG tags", () => {
    applySeo({ title: "Тест — Густо", description: "Описание страницы" });
    expect(document.title).toBe("Тест — Густо");
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute("content")).toBe(
      "Описание страницы",
    );
    expect(document.head.querySelector('meta[property="og:title"]')?.getAttribute("content")).toBe(
      "Тест — Густо",
    );
    expect(document.head.querySelector('meta[property="og:type"]')?.getAttribute("content")).toBe(
      "website",
    );
  });

  it("updates existing meta instead of duplicating", () => {
    applySeo({ title: "Первый", description: "a" });
    applySeo({ title: "Второй", description: "b" });
    expect(document.title).toBe("Второй");
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1);
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute("content")).toBe("b");
  });
});
