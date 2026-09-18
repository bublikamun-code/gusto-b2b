/**
 * Шаг количества весового товара (S19.1): quantity кратно step
 * (например фарш фасуется по 0.5 кг → 0.7 кг округляется до 0.5).
 * Без шага (step <= 0 / NaN) количество не меняется.
 */
export function roundToStep(quantity: number, step?: number | null): number {
  if (!Number.isFinite(quantity)) {
    return quantity;
  }
  if (!step || !Number.isFinite(step) || step <= 0) {
    return quantity;
  }
  const steps = Math.round(quantity / step);
  return Math.max(steps, 0) * step;
}

/** Сколько раз шаг укладывается в количестве (для подсказок в UI). */
export function stepCount(quantity: number, step?: number | null): number | null {
  if (!step || !Number.isFinite(step) || step <= 0 || !Number.isFinite(quantity)) {
    return null;
  }
  return Math.round(quantity / step);
}
