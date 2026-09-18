-- V15: S24 — счета: нумерация по sequence (2.2), ссылка на клиента и
-- целостность на уровне БД.

-- Формат СЧ-<порядковый> от <дата>; сквозная нумерация на год (ротация — S31)
CREATE SEQUENCE IF NOT EXISTS doc_seq_invoice_2026;

-- Прямая ссылка на компанию-покупателя (в V1 только order_id): списки кабинета
-- и скоупинг менеджера без джойна на заказы
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS customer_company_id UUID NULL REFERENCES companies(id);

-- Номер счёта уникален
CREATE UNIQUE INDEX IF NOT EXISTS ux_invoices_number ON invoices (number);

-- Один активный счёт на заказ: повторное выставление разрешено только после
-- отмены предыдущего (частичный уникальный индекс PostgreSQL)
CREATE UNIQUE INDEX IF NOT EXISTS ux_invoices_order_active
    ON invoices (order_id) WHERE status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_invoices_order ON invoices (order_id);
CREATE INDEX IF NOT EXISTS idx_invoices_company ON invoices (customer_company_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices (status);
