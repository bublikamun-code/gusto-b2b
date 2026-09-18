-- V14: S20 — заказы: склад резерва, контакты получателя, нумерация, идемпотентность.
-- idempotency_keys и carts/cart_items из V1 отсутствовали в baseline — создаются здесь.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS stock_location_id UUID NULL REFERENCES stock_locations(id),
    ADD COLUMN IF NOT EXISTS recipient_name TEXT NULL,
    ADD COLUMN IF NOT EXISTS recipient_phone TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_customer_user ON orders (customer_user_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_company ON orders (customer_company_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);

-- Нумерация заказов (2.2): З-<год>-<порядковый>; ротация на новый год — планировщик (S31)
CREATE SEQUENCE IF NOT EXISTS order_seq_2026;

-- Идемпотентность POST /orders и POST /site/requests (1.6): повтор с тем же ключом
-- возвращает сохранённый результат; TTL 24 ч, чистит планировщик
CREATE TABLE idempotency_keys (
    key           TEXT PRIMARY KEY,
    user_id       UUID NULL REFERENCES users(id),
    endpoint      TEXT NOT NULL,
    request_hash  TEXT NOT NULL,
    response      JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
