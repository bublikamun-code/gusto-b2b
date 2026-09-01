-- V6: демо-данные каталога для локальной разработки и приёмки S14.
-- В проде эти данные заменяются контентом из админки (S35–S36).

INSERT INTO categories (id, name, slug, sort) VALUES
  ('11111111-1111-1111-1111-111111111111'::uuid, 'Мясо',    'myaso',    1),
  ('22222222-2222-2222-2222-222222222222'::uuid, 'Птица',   'ptitsa',   2),
  ('33333333-3333-3333-3333-333333333333'::uuid, 'Яйца',    'yaytsa',   3),
  ('44444444-4444-4444-4444-444444444444'::uuid, 'Фарш',    'farsh',    4),
  ('55555555-5555-5555-5555-555555555555'::uuid, 'Колбаски','kolbaski', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO brands (id, name, slug) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Густо',           'gusto'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Ферма Заречье',   'zarechye')
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (sku, name, category_id, brand_id, description, unit, manufacturer, country, vat_rate, min_stock) VALUES
  ('steyk-ribay',          'Стейк рибай',          '11111111-1111-1111-1111-111111111111'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Мраморная говядина для жарки на сковороде-гриль', 'кг', 'Густо', 'РБ', 10, 0),
  ('steyk-na-kosti',       'Стейк на кости',       '11111111-1111-1111-1111-111111111111'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Сочный стейк на кости, охлаждённый', 'кг', 'Густо', 'РБ', 10, 0),
  ('vyrezka-svinaya',      'Вырезка свиная',       '11111111-1111-1111-1111-111111111111'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Необрезная свиная вырезка', 'кг', 'Ферма Заречье', 'РБ', 10, 0),
  ('bedro-kurinoye',       'Бедро куриное',        '22222222-2222-2222-2222-222222222222'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Куриное бедро охлаждённое, фермерское', 'кг', 'Густо', 'РБ', 10, 0),
  ('yaytsa-kurinye-s0',    'Яйца куриные С0',      '33333333-3333-3333-3333-333333333333'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Десяток крупных яиц категории С0', 'десяток', 'Ферма Заречье', 'РБ', 10, 0),
  ('farsh-govyazhiy',      'Фарш говяжий',         '44444444-4444-4444-4444-444444444444'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Свежий говяжий фарш, жирность 20%', 'кг', 'Густо', 'РБ', 10, 0),
  ('farsh-kuriniy',        'Фарш куриный',         '44444444-4444-4444-4444-444444444444'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'Куриный фарш домашний', 'кг', 'Густо', 'РБ', 10, 0),
  ('kolbaski-dlya-zharki', 'Колбаски для жарки',   '55555555-5555-5555-5555-555555555555'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'Домашние колбаски для жарки на гриле', 'кг', 'Ферма Заречье', 'РБ', 10, 0)
ON CONFLICT (sku) DO NOTHING;

INSERT INTO price_lists (id, name, valid_from, valid_to, is_active) VALUES
  ('cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid, 'Розничный прайс-лист 2026', '2026-01-01', '2026-12-31', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_prices (price_list_id, product_id, price, valid_from, valid_to)
SELECT 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid, p.id, t.price, '2026-01-01', '2026-12-31'
FROM products p
JOIN (VALUES
  ('steyk-ribay',          42.50),
  ('steyk-na-kosti',       38.90),
  ('vyrezka-svinaya',      24.90),
  ('bedro-kurinoye',       11.40),
  ('yaytsa-kurinye-s0',     4.80),
  ('farsh-govyazhiy',      18.70),
  ('farsh-kuriniy',        12.50),
  ('kolbaski-dlya-zharki', 21.30)
) AS t(sku, price) ON p.sku = t.sku
ON CONFLICT (price_list_id, product_id, valid_from) DO UPDATE SET price = EXCLUDED.price;
