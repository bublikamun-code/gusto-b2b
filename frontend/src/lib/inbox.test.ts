import { describe, expect, it } from "vitest";
import { isStale } from "./inbox";

describe("isStale (S30 единое окно)", () => {
  const now = new Date("2026-09-19T12:00:00Z");

  it("старше 24 часов — требует реакции", () => {
    expect(isStale("2026-09-18T11:00:00Z", now)).toBe(true);
  });

  it("свежая заявка — не stale", () => {
    expect(isStale("2026-09-19T10:00:00Z", now)).toBe(false);
  });

  it("ровно 24 часа — уже stale", () => {
    expect(isStale("2026-09-18T12:00:00Z", now)).toBe(true);
  });

  it("некорректная дата — не падает", () => {
    expect(isStale("not-a-date", now)).toBe(false);
  });
});
