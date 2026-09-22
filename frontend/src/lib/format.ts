/** Деньги, формат бренд-бука — суффикс «р.» (2.3: цены НДС-включённые). */
export function formatMoney(value: number): string {
  return `${new Intl.NumberFormat("ru-BY", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)} р.`;
}
