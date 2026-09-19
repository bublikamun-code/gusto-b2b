import { describe, expect, it } from "vitest";
import { validateSiteRequest } from "../api/siteRequests";

describe("validateSiteRequest (S34 публичная форма)", () => {
  it("требует имя", () => {
    expect(validateSiteRequest({ name: "  ", type: "WHOLESALE" })).toBe("Укажите имя");
    expect(validateSiteRequest({ name: "И", type: "WHOLESALE" })).toBe("Укажите имя");
  });

  it("требует телефон или email", () => {
    expect(validateSiteRequest({ name: "Иван", type: "WHOLESALE" }))
        .toBe("Укажите телефон или email для связи");
    expect(validateSiteRequest({ name: "Иван", type: "WHOLESALE", phone: "123" }))
        .toBe("Укажите телефон или email для связи");
  });

  it("принимает заявку с телефоном или корректным email", () => {
    expect(validateSiteRequest({ name: "Иван", type: "WHOLESALE", phone: "+375 29 000-00-00" })).toBeNull();
    expect(validateSiteRequest({ name: "Иван", type: "WHOLESALE", email: "ivan@test.by" })).toBeNull();
  });

  it("отклоняет некорректный email при отсутствии телефона", () => {
    expect(validateSiteRequest({ name: "Иван", type: "WHOLESALE", email: "не-почта" }))
        .toBe("Укажите телефон или email для связи");
  });
});
