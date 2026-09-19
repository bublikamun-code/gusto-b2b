/** Порог «без ответа»: 24 часа (S30, единое окно входящих). */
export const STALE_THRESHOLD_HOURS = 24;

/** Заявка/заказ без назначения старше порога — требует реакции. */
export function isStale(createdAtIso: string, now: Date = new Date()): boolean {
  const created = new Date(createdAtIso).getTime();
  if (Number.isNaN(created)) return false;
  return now.getTime() - created >= STALE_THRESHOLD_HOURS * 3600_000;
}
