-- V25: демо-данные бизнес-процесса для локальной разработки, приёмки и демонстрации.
--
-- Зачем: до этой миграции в базе был один админ, одна компания и два заказа, созданных вручную.
-- Роли ACCOUNTANT и MANAGER не существовали ВООБЩЕ — то есть половина маршрутов (кабинет
-- бухгалтера, «Единое окно входящих», склад, CRM) физически нечем было открыть и нечем было
-- принять. Именно поэтому дефекты изоляции данных (аудит 2026-09-30) пережили два аудита:
-- в данных не было места, где они могли бы проявиться.
--
-- Здесь создаётся полный демо-контур: персонал, компании, заказы на всех статусах,
-- счёт с частичной оплатой, накладная, CRM-воронка, закупки. Пароль всех демо-устройств —
-- 'gusto-demo' (только для локальной БД; в проде эти учётки не создаются).
--
-- Идемпотентно: повторный прогон Flyway на уже заполненной базе ничего не ломает.

-- =============================================================================
-- Персонал
-- =============================================================================
INSERT INTO users (email, password_hash, full_name, phone, role, is_active)
VALUES
  ('accountant@gustomeat.by', crypt('gusto-demo', gen_salt('bf')), 'Анна Бухгалтерова', '+375291110001', 'ACCOUNTANT', TRUE),
  ('manager@gustomeat.by',    crypt('gusto-demo', gen_salt('bf')), 'Пётр Менеджеров',    '+375291110002', 'MANAGER',    TRUE),
  ('manager2@gustomeat.by',   crypt('gusto-demo', gen_salt('bf')), 'Мария Менеджерова',  '+375291110003', 'MANAGER',    TRUE)
ON CONFLICT (email) DO NOTHING;

-- =============================================================================
-- Компании. manager_id разводим по двум менеджерам — иначе изоляцию
-- «менеджер видит только своих клиентов» не на чем проверить.
-- =============================================================================
INSERT INTO companies (id, name, short_name, unp, legal_address, actual_address,
                       bank_account, bank_name, bank_bic, contact_phone, contact_email, manager_id)
VALUES
  ('c1000000-0000-4000-8000-000000000001', 'ООО «БелМясоТрейд»', 'БелМясоТрейд', '191536521',
   '220030 г. Минск, ул. Ленинградская, д. 15, оф. 4', 'г. Минск, Серебрянская, 12',
   'BY07NBRB3600900000002Z00AB00', 'БНБ-Беларусбанк', 'NBRBY2XM',
   '+375291020001', 'info@belmyaso.by',
   (SELECT id FROM users WHERE email = 'manager@gustomeat.by')),
  ('c1000000-0000-4000-8000-000000000002', 'ООО «МясоОптГрупп»', 'МясоОптГрупп', '191775412',
   '223053 г. Минск, пр. Победителей, д. 100', 'г. Минск, Победителей, 100',
   'BY11NBRB3604900000002Z00AZ11', 'Белагропромбанк', 'NBRBY2X7',
   '+375291020002', 'sales@myasoopt.by',
   (SELECT id FROM users WHERE email = 'manager2@gustomeat.by'))
ON CONFLICT (id) DO NOTHING;

UPDATE companies SET manager_id = (SELECT id FROM users WHERE email = 'manager@gustomeat.by')
WHERE id = 'c1000000-0000-4000-8000-000000000001' AND manager_id IS NULL;

UPDATE companies SET manager_id = (SELECT id FROM users WHERE email = 'manager2@gustomeat.by')
WHERE id = 'c1000000-0000-4000-8000-000000000002' AND manager_id IS NULL;

-- =============================================================================
-- Клиенты: два юрлица (по одному на менеджера) и розничное физлицо
-- =============================================================================
INSERT INTO users (email, password_hash, full_name, phone, role, company_id, email_confirmed_at)
VALUES
  ('client@test.by',       crypt('change-me', gen_salt('bf')), 'Иван Тестов',    '+375291030001', 'CUSTOMER_LEGAL',      'c1000000-0000-4000-8000-000000000001', now()),
  ('client2@test.by',      crypt('gusto-demo', gen_salt('bf')), 'Ольга Клиентова', '+375291030002', 'CUSTOMER_LEGAL',      'c1000000-0000-4000-8000-000000000002', now()),
  ('retail@test.by',       crypt('gusto-demo', gen_salt('bf')), 'Пётр Покупатель', '+375291030003', 'CUSTOMER_INDIVIDUAL', NULL, now()),
  ('nopass@test.by',       crypt('change-me', gen_salt('bf')), 'Без Пароля',     '+375291030004', 'CUSTOMER_LEGAL',      NULL, now())
ON CONFLICT (email) DO NOTHING;

UPDATE users SET company_id = 'c1000000-0000-4000-8000-000000000001'
WHERE email = 'client@test.by' AND company_id IS NULL;

-- =============================================================================
-- Персональная цена и скидка для клиента — иначе «Прайс и скидки» (S47) пуст
-- =============================================================================
INSERT INTO customer_discounts (id, company_id, brand_id, category_id, discount_percent, valid_from, valid_to)
VALUES
  ('d1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', NULL, NULL, 15.00, DATE '2026-01-01', DATE '2027-12-31'),
  ('d1000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', NULL, NULL, 10.00, DATE '2026-01-01', DATE '2027-12-31')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_prices (company_id, product_id, price, valid_from, valid_to)
SELECT 'c1000000-0000-4000-8000-000000000001', p.id, 32.00, DATE '2026-01-01', DATE '2027-12-31'
FROM products p WHERE p.sku = 'steyk-ribay'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- Заказы: по одному на каждую статусу машины 2.8 + розничный в пул «не назначено»
-- =============================================================================
INSERT INTO orders (id, number, customer_company_id, customer_user_id, manager_id, status,
                    delivery_type, delivery_address, recipient_name, recipient_phone, note,
                    stock_location_id, total_amount, total_vat, created_at)
VALUES
  ('e1000000-0000-4000-8000-000000000001', 'З-2026-00101', 'c1000000-0000-4000-8000-000000000001',
   (SELECT id FROM users WHERE email = 'client@test.by'), (SELECT id FROM users WHERE email = 'manager@gustomeat.by'),
   'COMPLETED', 'DELIVERY', 'г. Минск, ул. Серебрянская, 12', 'Иван Тестов', '+375291030001',
   'Еженедельная поставка', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 1290.00, 117.27, now() - interval '12 days'),

  ('e1000000-0000-4000-8000-000000000002', 'З-2026-00102', 'c1000000-0000-4000-8000-000000000002',
   (SELECT id FROM users WHERE email = 'client2@test.by'), (SELECT id FROM users WHERE email = 'manager2@gustomeat.by'),
   'SHIPPED', 'PICKUP', NULL, 'Ольга Клиентова', '+375291030002',
   'Самоввоз со склада', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 842.00, 76.55, now() - interval '6 days'),

  ('e1000000-0000-4000-8000-000000000003', 'З-2026-00103', 'c1000000-0000-4000-8000-000000000001',
   (SELECT id FROM users WHERE email = 'client@test.by'), (SELECT id FROM users WHERE email = 'manager@gustomeat.by'),
   'READY', 'DELIVERY', 'г. Минск, ул. Серебрянская, 12', 'Иван Тестов', '+375291030001',
   'Ждут машину', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 595.00, 54.09, now() - interval '2 days'),

  ('e1000000-0000-4000-8000-000000000004', 'З-2026-00104', 'c1000000-0000-4000-8000-000000000002',
   (SELECT id FROM users WHERE email = 'client2@test.by'), NULL,
   'NEW', 'DELIVERY', 'г. Минск, пр. Победителей, д. 100', 'Ольга Клиентова', '+375291030002',
   'Без менеджера — пул', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 311.40, 28.31, now() - interval '8 hours'),

  ('e1000000-0000-4000-8000-000000000005', 'З-2026-00105', NULL,
   (SELECT id FROM users WHERE email = 'retail@test.by'), NULL,
   'NEW', 'PICKUP', NULL, 'Пётр Покупатель', '+375291030003',
   'Розничный заказ в пул', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 85.00, 7.73, now() - interval '3 hours')
ON CONFLICT DO NOTHING;

-- Позиции заказов: снапшот как в OrderService (S20)
INSERT INTO order_items (order_id, product_id, product_snapshot, quantity, unit_price, vat_rate, total)
SELECT o.id, p.id,
       jsonb_build_object('sku', p.sku, 'name', p.name, 'unit', p.unit, 'vatRate', p.vat_rate),
       t.qty, t.price, p.vat_rate, ROUND(t.qty * t.price, 2)
FROM (VALUES
  ('З-2026-00101', 'steyk-ribay',         20.000::numeric, 32.00::numeric),
  ('З-2026-00101', 'farsh-govyazhiy',     15.000::numeric, 18.70::numeric),
  ('З-2026-00102', 'bedro-kurinoye',      40.000::numeric, 11.40::numeric),
  ('З-2026-00102', 'vyrezka-svinaya',     15.000::numeric, 24.90::numeric),
  ('З-2026-00103', 'steyk-na-kosti',      10.000::numeric, 38.90::numeric),
  ('З-2026-00103', 'yaytsa-kurinye-s0',   30.000::numeric,  4.80::numeric),
  ('З-2026-00104', 'farsh-kuriniy',        8.000::numeric, 12.50::numeric),
  ('З-2026-00105', 'kolbaski-dlya-zharki',  4.000::numeric, 21.30::numeric)
) AS t(num, sku, qty, price)
JOIN orders o    ON o.number = t.num
JOIN products p  ON p.sku = t.sku
WHERE NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id);

-- Итоги шапки считаем из позиций, а не пишем руками: демо-суммы обязаны сходиться,
-- иначе счёт, накладная и «остаток к оплате» показывают разные числа (2.3).
-- НДС расчётный: сумма уже включает налог, VAT = total × rate / (100 + rate).
UPDATE orders o SET
    total_amount = agg.total_amount,
    total_vat    = agg.total_vat
FROM (
    SELECT oi.order_id,
           SUM(oi.total)                                AS total_amount,
           ROUND(SUM(oi.total * oi.vat_rate / (100 + oi.vat_rate)), 2) AS total_vat
    FROM order_items oi
    WHERE oi.order_id IN (SELECT id FROM orders WHERE number LIKE 'З-2026-001%')
    GROUP BY oi.order_id
) agg
WHERE o.id = agg.order_id;

-- =============================================================================
-- Счёт с частичной оплатой и накладная: иначе «Остаток к оплате» (S47)
-- и блок оплат в карточке счёта проверять не на чем.
-- =============================================================================
INSERT INTO invoices (id, number, series, issue_date, order_id, seller_snapshot, buyer_snapshot,
                      total_amount, total_vat, status, customer_company_id, created_by, created_at)
SELECT 'f1000000-0000-4000-8000-000000000001', 'СЧ-00001', 'A', DATE '2026-09-20',
       o.id,
       '{"name":"ЧТУП «ЛорСан»","unp":"191536521","address":"220028 г. Минск, ул. Бородинская, д. 1Б","bank_account":"","bank_name":"","bank_bic":""}'::jsonb,
       '{"name":"ООО «БелМясоТрейд»","unp":"191536521","address":"220030 г. Минск, ул. Ленинградская, д. 15","bank_account":"BY07NBRB3600900000002Z00AB00","bank_name":"БНБ-Беларусбанк","bank_bic":"NBRBY2XM"}'::jsonb,
       o.total_amount, o.total_vat, 'PARTIALLY_PAID', o.customer_company_id,
       (SELECT id FROM users WHERE email = 'accountant@gustomeat.by'), now() - interval '11 days'
FROM orders o WHERE o.number = 'З-2026-00101'
ON CONFLICT DO NOTHING;

INSERT INTO invoice_items (invoice_id, product_snapshot, quantity, unit_price, vat_rate, total)
SELECT i.id, oi.product_snapshot, oi.quantity, oi.unit_price, oi.vat_rate, oi.total
FROM invoices i
JOIN orders o       ON o.id = i.order_id
JOIN order_items oi ON oi.order_id = o.id
WHERE i.number = 'СЧ-00001'
  AND NOT EXISTS (SELECT 1 FROM invoice_items x WHERE x.invoice_id = i.id);

-- Частичная оплата — половина счёта (S28), чтобы «Остаток» в кабинете был не нулевым
INSERT INTO payments (invoice_id, amount, paid_at, method, note, created_by)
SELECT i.id, ROUND(i.total_amount / 2, 2), DATE '2026-09-25', 'BANK_TRANSFER',
       'Первый транш', (SELECT id FROM users WHERE email = 'accountant@gustomeat.by')
FROM invoices i
WHERE i.id = 'f1000000-0000-4000-8000-000000000001'
  AND NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = i.id);

INSERT INTO waybills (id, type, number, series, issue_date, order_id, seller_snapshot, buyer_snapshot,
                      carrier_snapshot, created_by, created_at)
SELECT 'a1000000-0000-4000-8000-000000000001', 'TTN', 'ТТН-A-00001', 'A', DATE '2026-09-26', o.id,
       '{"name":"ЧТУП «ЛорСан»","unp":"191536521","address":"220028 г. Минск, ул. Бородинская, д. 1Б"}'::jsonb,
       '{"name":"ООО «БелМясоТрейд»","unp":"191536521","address":"220030 г. Минск, ул. Ленинградская, д. 15"}'::jsonb,
       '{"vehicle":"MAZ-5337","driver":"Петров П.П.","carrierCompany":"ТрансЛогистика"}'::jsonb,
       (SELECT id FROM users WHERE email = 'accountant@gustomeat.by'), now() - interval '10 days'
FROM orders o WHERE o.number = 'З-2026-00101'
ON CONFLICT DO NOTHING;

INSERT INTO waybill_items (waybill_id, product_snapshot, quantity, unit_price, vat_rate, total, weight)
SELECT 'a1000000-0000-4000-8000-000000000001', oi.product_snapshot, oi.quantity, oi.unit_price,
       oi.vat_rate, oi.total, ROUND(oi.quantity * 1.000, 3)
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.number = 'З-2026-00101'
  AND NOT EXISTS (SELECT 1 FROM waybill_items w WHERE w.waybill_id = 'a1000000-0000-4000-8000-000000000001'::uuid);

-- =============================================================================
-- CRM: воронка на всех статусах, просроченная задача, заметка
-- =============================================================================
INSERT INTO leads (id, source, name, phone, email, company_name, message, status, assigned_manager_id, created_at)
VALUES
  ('b1000000-0000-4000-8000-000000000001', 'SITE', 'Антон Сайтов', '+375291040001', 'anton@example.by',
   'ООО «Рестораны Минска»', 'Нужен поставщик мяса для сети ресторанов', 'NEW', NULL, now() - interval '3 days'),
  ('b1000000-0000-4000-8000-000000000002', 'SITE', 'Борис Заявкин', '+375291040002', NULL,
   NULL, 'Сколько стоит свиная вырезка оптом?', 'IN_PROGRESS',
   (SELECT id FROM users WHERE email = 'manager@gustomeat.by'), now() - interval '5 days'),
  ('b1000000-0000-4000-8000-000000000003', 'REFERRAL', 'Вера Клиентова', '+375291040003', NULL,
   'ООО «МясоОптГрупп»', 'Клиент рекомендовал', 'QUALIFIED',
   (SELECT id FROM users WHERE email = 'manager2@gustomeat.by'), now() - interval '7 days'),
  ('b1000000-0000-4000-8000-000000000004', 'SITE', 'Глеб Говорящий', '+375291040004', NULL,
   NULL, 'Хочу обсудить условия', 'WON',
   (SELECT id FROM users WHERE email = 'manager@gustomeat.by'), now() - interval '20 days'),
  ('b1000000-0000-4000-8000-000000000005', 'SITE', 'Дарья Разбитая', '+375291040005', NULL,
   NULL, 'Слишком дорого', 'LOST', NULL, now() - interval '30 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_tasks (assignee_id, company_id, title, description, due_date, status)
SELECT (SELECT id FROM users WHERE email = 'manager@gustomeat.by'), 'c1000000-0000-4000-8000-000000000001',
       'Позвонить по заказу З-2026-00103', 'Клиент ждёт машину', DATE '2026-09-20', 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM crm_tasks WHERE title = 'Позвонить по заказу З-2026-00103');

INSERT INTO crm_tasks (assignee_id, company_id, title, description, due_date, status)
SELECT (SELECT id FROM users WHERE email = 'manager2@gustomeat.by'), 'c1000000-0000-4000-8000-000000000002',
       'Согласовать условия по ТТН', 'Позвонить в транспортную', DATE '2026-09-27', 'DONE'
WHERE NOT EXISTS (SELECT 1 FROM crm_tasks WHERE title = 'Согласовать условия по ТТН');

INSERT INTO crm_notes (company_id, author_id, body)
SELECT 'c1000000-0000-4000-8000-000000000001', (SELECT id FROM users WHERE email = 'manager@gustomeat.by'),
       'Клиент работает с нами второй год, просит отгрузку по вторникам.'
WHERE NOT EXISTS (SELECT 1 FROM crm_notes WHERE body LIKE 'Клиент работает с нами второй год%');

-- =============================================================================
-- Закупки: поставщик, заказ поставщику (SENT) и складской документ (CONFIRMED),
-- чтобы «Склад» открывался не пустым.
-- =============================================================================
INSERT INTO suppliers (id, name, unp, phone, email, contact_person, note)
VALUES
  ('91000000-0000-4000-8000-000000000001', 'Ферма «Заречье»', '191322334', '+375294050001',
   'sales@zarechye.by', 'Николай', 'Основной поставщик свинины'),
  ('91000000-0000-4000-8000-000000000002', 'Птицефабрика «Зadrожье»', '191455667', '+375294050002',
   NULL, 'Мария', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO purchase_orders (id, number, supplier_id, status, expected_date, total_amount, note, created_by, created_at)
SELECT '92000000-0000-4000-8000-000000000001', 'ЗП-00001', '91000000-0000-4000-8000-000000000001',
       'SENT', DATE '2026-10-10', 4200.00, 'Плановое пополнение',
       (SELECT id FROM users WHERE email = 'manager@gustomeat.by'), now() - interval '4 days'
ON CONFLICT DO NOTHING;

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, purchase_price)
SELECT '92000000-0000-4000-8000-000000000001', p.id, t.qty, 0, t.price
FROM (VALUES
  ('vyrezka-svinaya',     100.000::numeric, 22.00::numeric),
  ('bedro-kurinoye',       80.000::numeric, 10.00::numeric)
) AS t(sku, qty, price)
JOIN products p ON p.sku = t.sku
WHERE NOT EXISTS (
  SELECT 1 FROM purchase_order_items x WHERE x.purchase_order_id = '92000000-0000-4000-8000-000000000001'::uuid
);
