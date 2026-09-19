# Формат экспорта для 1С (S36)

Колонки согласованы с бухгалтерией. Кодировка — XLSX (UTF-8). Даты — `DD.MM.YYYY`.

## Выгрузка заказов (`GET /api/v1/admin/export/orders?from&to`)

| Колонка | Источник | Пример |
|---|---|---|
| Номер | `orders.number` | З-2026-00042 |
| Дата | `orders.created_at` | 19.09.2026 |
| Статус | `orders.status` | COMPLETED |
| Доставка | `orders.delivery_type` | DELIVERY / PICKUP |
| Клиент | `companies.name` либо «Розница» | ООО «ТиоптТрейд» |
| Сумма, BYN | `orders.total_amount` | 85.00 |
| НДС, BYN | `orders.total_vat` (расчётно, 2.3) | 7.73 |

## Выгрузка счетов (`GET /api/v1/admin/export/invoices?from&to`)

| Колонка | Источник | Пример |
|---|---|---|
| Счёт | `invoices.number` | СЧ-1 |
| Дата | `invoices.issue_date` | 19.09.2026 |
| Статус | `invoices.status` | ISSUED / PARTIALLY_PAID / PAID |
| Покупатель | `companies.name` | ООО «ТиоптТрейд» |
| УНП | `companies.unp` | 192953686 |
| Сумма, BYN | `invoices.total_amount` | 85.00 |
| НДС, BYN | `invoices.total_vat` | 7.73 |

## Выгрузка накладных (`GET /api/v1/admin/export/waybills?from&to`)

| Колонка | Источник | Пример |
|---|---|---|
| Накладная | `waybills.number` | ТТН-А-1 |
| Тип | `waybills.type` | TN / TTN |
| Дата | `waybills.issue_date` | 19.09.2026 |
| Покупатель | `companies.name` | ООО «ТиоптТрейд» |
| Сумма, BYN | сумма позиций | 85.00 |

## Прайс клиента (`GET /api/v1/cabinet/pricing/export`, юрлицо)

| Колонка | Источник |
|---|---|
| Артикул | `products.sku` |
| Наименование | `products.name` |
| Ед. | `products.unit` |
| Вес ед., кг | `products.weight_per_unit` |
| Цена, BYN | персональная цена (customer_prices) либо базовая цена активного прайс-листа |

Все суммы — НДС-включённые (2.3).
