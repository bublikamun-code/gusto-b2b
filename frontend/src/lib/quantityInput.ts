/**
 * Разбор draft-строки поля количества в корзине (plan-04):
 * локальный ввод коммитится по blur/Enter, поэтому строку валидируем
 * перед отправкой. Пустая/невалидная строка и «0» на сервер не уходят —
 * quantity=0 (удаление позиции) разрешён только кнопке «Убрать».
 */
export type QuantityDraft =
  | { kind: "ok"; value: number }
  | { kind: "empty" }
  | { kind: "zero" }
  | { kind: "invalid" };

export function parseQuantityInput(raw: string): QuantityDraft {
  const trimmed = raw.trim().replace(",", ".");
  if (trimmed === "") {
    return { kind: "empty" };
  }
  const value = Number(trimmed);
  if (!Number.isFinite(value) || value < 0) {
    return { kind: "invalid" };
  }
  if (value === 0) {
    return { kind: "zero" };
  }
  return { kind: "ok", value };
}
