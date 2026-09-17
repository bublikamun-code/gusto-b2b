/** Деньги в BYN, ru-RU-подобное форматирование (2.3: цены НДС-включённые). */
export function formatMoney(value: number): string {
  return new Intl.NumberFormat("ru-BY", {
    style: "currency",
    currency: "BYN",
    minimumFractionDigits: 2,
  }).format(value);
}
