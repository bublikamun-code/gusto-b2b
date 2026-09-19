import { apiRequest } from "./client";

/**
 * Публичная заявка с сайта (S31/S34): без авторизации; бэкенд ограничивает
 * частоту (10/час на IP) и поддерживает идемпотентность по Idempotency-Key.
 */
export type SiteRequestType = "CALLBACK" | "WHOLESALE" | "RETAIL" | "OTHER";

export interface SiteRequestForm {
  name: string;
  phone?: string;
  email?: string;
  type: SiteRequestType;
  message?: string;
}

export function createSiteRequest(form: SiteRequestForm) {
  return apiRequest<{ id: string; status: string }>("/site/requests", {
    method: "POST",
    body: form,
    headers: { "Idempotency-Key": crypto.randomUUID() },
  });
}

/** Базовая валидация формы до отправки (бэкенд валидирует повторно). */
export function validateSiteRequest(form: SiteRequestForm): string | null {
  if (!form.name || form.name.trim().length < 2) {
    return "Укажите имя";
  }
  const hasPhone = form.phone && form.phone.trim().length >= 7;
  const hasEmail = form.email && /.+@.+\..+/.test(form.email);
  if (!hasPhone && !hasEmail) {
    return "Укажите телефон или email для связи";
  }
  return null;
}
