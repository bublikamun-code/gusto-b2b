-- V9: S18 — материализованные остатки, вьюхи, склад по умолчанию, демо-остатки.
-- stock_balances — ЕДИНСТВЕННОЕ место блокировки резерва (SELECT ... FOR UPDATE, 1.6);
-- stock_movements остаётся журналом и уже создан в V1.

CREATE TABLE stock_balances (
    product_id     UUID NOT NULL REFERENCES products(id),
    location_id    UUID NOT NULL REFERENCES stock_locations(id),
    quantity       NUMERIC(12,3) NOT NULL DEFAULT 0,
    reserved       NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, location_id)
);

-- Отчётные вьюхи поверх материализованного остатка (S18.3 добавит turnover/to_order)
CREATE VIEW v_stock_balance AS
SELECT b.product_id,
       p.sku,
       p.name AS product_name,
       b.location_id,
       l.name AS location_name,
       b.quantity,
       b.reserved
FROM stock_balances b
JOIN products p ON p.id = b.product_id
JOIN stock_locations l ON l.id = b.location_id;

CREATE VIEW v_stock_available AS
SELECT product_id,
       location_id,
       quantity - reserved AS available
FROM stock_balances;

-- Склад по умолчанию: с него идёт резерв заказа (1.6 «Склад и заказ»)
INSERT INTO stock_locations (id, name, address)
VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid, 'Основной склад', 'г. Минск')
ON CONFLICT (id) DO NOTHING;

INSERT INTO settings (key, value)
VALUES ('stock.default_location', '"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"'::jsonb)
ON CONFLICT (key) DO NOTHING;

-- Демо-остатки: журнал и баланс согласованы (начальный приход), в проде придут из 1С (S35)
WITH incoming (sku, qty) AS (VALUES
    ('steyk-ribay',          40.000),
    ('steyk-na-kosti',       25.000),
    ('vyrezka-svinaya',      30.000),
    ('bedro-kurinoye',       60.000),
    ('yaytsa-kurinye-s0',   120.000),
    ('farsh-govyazhiy',      50.000),
    ('farsh-kuriniy',        0.000),
    ('kolbaski-dlya-zharki', 35.000)
)
INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, note)
SELECT p.id, 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid, 'INCOMING', i.qty,
       'SEED', 'Начальный остаток демо-данных (V9)'
FROM incoming i
JOIN products p ON p.sku = i.sku;

WITH incoming (sku, qty) AS (VALUES
    ('steyk-ribay',          40.000),
    ('steyk-na-kosti',       25.000),
    ('vyrezka-svinaya',      30.000),
    ('bedro-kurinoye',       60.000),
    ('yaytsa-kurinye-s0',   120.000),
    ('farsh-govyazhiy',      50.000),
    ('farsh-kuriniy',        0.000),
    ('kolbaski-dlya-zharki', 35.000)
)
INSERT INTO stock_balances (product_id, location_id, quantity, reserved)
SELECT p.id, 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid, i.qty, 0
FROM incoming i
JOIN products p ON p.sku = i.sku;
