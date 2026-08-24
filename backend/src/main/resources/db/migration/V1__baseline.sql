-- V1: baseline-схема GUSTO B2B (план, Часть 3.1)
-- Все таблицы, индексы, CHECK-ограничения. Сиды — V2. Вьюхи склада — S18.

CREATE EXTENSION IF NOT EXISTS citext;   -- email CITEXT
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- поиск по названию товара (индекс — в S13)

-- =============================================================================
-- Компании и пользователи
-- (companies.manager_id ↔ users.company_id — циклическая связь: FK на users
--  добавляется ALTER'ом после создания users)
-- =============================================================================

CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    short_name      TEXT,
    unp             TEXT UNIQUE,
    legal_address   TEXT,
    actual_address  TEXT,
    bank_account    TEXT,
    bank_name       TEXT,
    bank_bic        TEXT,
    contact_phone   TEXT,
    contact_email   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    manager_id      UUID,                -- FK добавляется ниже
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           CITEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    full_name       TEXT NOT NULL,
    phone           TEXT,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','ACCOUNTANT','MANAGER','CUSTOMER_LEGAL','CUSTOMER_INDIVIDUAL')),
    company_id      UUID REFERENCES companies(id),
    totp_secret     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ          -- soft delete (план 1.6)
);

ALTER TABLE companies
    ADD CONSTRAINT companies_manager_id_fk
    FOREIGN KEY (manager_id) REFERENCES users(id);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      TEXT NOT NULL UNIQUE,
    user_agent      TEXT,
    ip              TEXT,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE
);

-- =============================================================================
-- Файлы (модуль files, S17; публичные — непредсказуемый storage_key, план 1.6)
-- =============================================================================

CREATE TABLE files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key     TEXT NOT NULL UNIQUE,
    original_name   TEXT NOT NULL,
    mime_type       TEXT NOT NULL,
    size_bytes      BIGINT NOT NULL,
    checksum        TEXT,
    owner_id        UUID REFERENCES users(id),
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PUBLIC','PRIVATE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- Каталог
-- =============================================================================

CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id       UUID REFERENCES categories(id),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL UNIQUE,
    sort            INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE brands (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    slug            TEXT NOT NULL UNIQUE
);

CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    category_id     UUID NOT NULL REFERENCES categories(id),
    description     TEXT,
    unit            TEXT NOT NULL DEFAULT 'кг',
    manufacturer    TEXT,
    country         TEXT NOT NULL DEFAULT 'РБ',
    tnved_code      TEXT,
    barcode         TEXT,
    vat_rate        NUMERIC(4,2) NOT NULL DEFAULT 10,
    weight_per_unit NUMERIC(10,3),
    min_stock       NUMERIC(12,3) NOT NULL DEFAULT 0,  -- мин. остаток для уведомлений (S18.3)
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ                        -- soft delete (план 1.6)
);

CREATE TABLE product_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    file_id         UUID NOT NULL REFERENCES files(id),
    sort            INT NOT NULL DEFAULT 0
);

-- =============================================================================
-- Цены (приоритет: персональная → скидка → базовая, план 2.5)
-- =============================================================================

CREATE TABLE price_lists (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE product_prices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_list_id   UUID NOT NULL REFERENCES price_lists(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    price           NUMERIC(12,2) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    UNIQUE (price_list_id, product_id, valid_from)
);

CREATE TABLE customer_prices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    price           NUMERIC(12,2) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    UNIQUE (company_id, product_id, valid_from)
);

CREATE TABLE customer_discounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    brand_id        UUID REFERENCES brands(id),
    category_id     UUID REFERENCES categories(id),
    discount_percent NUMERIC(5,2) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE
);

-- =============================================================================
-- Склад
-- =============================================================================

CREATE TABLE stock_locations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    address         TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE suppliers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    unp             TEXT,
    phone           TEXT,
    email           TEXT,
    contact_person  TEXT,
    note            TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- Заказы клиентов
-- =============================================================================

CREATE TABLE orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number               TEXT NOT NULL UNIQUE,
    customer_company_id  UUID REFERENCES companies(id),   -- NULL для физлиц
    customer_user_id     UUID NOT NULL REFERENCES users(id),
    manager_id           UUID REFERENCES users(id),
    status               VARCHAR(20) NOT NULL DEFAULT 'NEW'
                         CHECK (status IN ('NEW','CONFIRMED','PROCESSING','READY','SHIPPED','COMPLETED','CANCELLED')),
    delivery_type        VARCHAR(20) NOT NULL CHECK (delivery_type IN ('PICKUP','DELIVERY')),
    delivery_address     TEXT,
    note                 TEXT,
    total_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_vat            NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL REFERENCES orders(id),
    product_id       UUID NOT NULL REFERENCES products(id),
    product_snapshot JSONB NOT NULL,             -- снапшот на момент заказа (план 3.2)
    quantity         NUMERIC(12,3) NOT NULL,
    unit_price       NUMERIC(12,2) NOT NULL,
    vat_rate         NUMERIC(4,2) NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    note             TEXT
);

-- =============================================================================
-- Закупки у поставщиков
-- =============================================================================

CREATE TABLE purchase_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number          TEXT NOT NULL UNIQUE,
    supplier_id     UUID NOT NULL REFERENCES suppliers(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','SENT','PARTIAL','RECEIVED','CANCELLED')),
    expected_date   DATE,
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    note            TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE purchase_order_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id  UUID NOT NULL REFERENCES purchase_orders(id),
    product_id         UUID NOT NULL REFERENCES products(id),
    quantity           NUMERIC(12,3) NOT NULL,
    purchase_price     NUMERIC(12,2) NOT NULL,
    received_quantity  NUMERIC(12,3) NOT NULL DEFAULT 0
);

-- =============================================================================
-- Складские документы и движения
-- Движения создаются ТОЛЬКО при CONFIRMED складского документа или заказа.
-- =============================================================================

CREATE TABLE warehouse_documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number             TEXT NOT NULL UNIQUE,
    type               VARCHAR(15) NOT NULL
                       CHECK (type IN ('INCOMING','OUTGOING','TRANSFER','WRITE_OFF','INVENTORY')),
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
    location_from_id   UUID REFERENCES stock_locations(id),
    location_to_id     UUID REFERENCES stock_locations(id),
    supplier_id        UUID REFERENCES suppliers(id),
    purchase_order_id  UUID REFERENCES purchase_orders(id),
    customer_order_id  UUID REFERENCES orders(id),     -- для расхода по заказу клиента
    document_date      DATE NOT NULL,
    note               TEXT,
    created_by         UUID NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at       TIMESTAMPTZ
);

CREATE TABLE warehouse_document_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES warehouse_documents(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        NUMERIC(12,3) NOT NULL,
    price           NUMERIC(12,2)
);

-- Остаток = SUM(quantity) по (product_id, location_id); доступно = остаток − резерв
CREATE TABLE stock_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    location_id     UUID NOT NULL REFERENCES stock_locations(id),
    type            VARCHAR(10) NOT NULL
                    CHECK (type IN ('INCOMING','OUTGOING','ADJUSTMENT','RESERVE','RELEASE')),
    quantity        NUMERIC(12,3) NOT NULL,
    reference_type  TEXT,
    reference_id    UUID,
    note            TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_product_location ON stock_movements (product_id, location_id);
CREATE INDEX idx_stock_movements_created_at ON stock_movements (created_at);

-- =============================================================================
-- Корзина (persist для авторизованных)
-- =============================================================================

CREATE TABLE carts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID NOT NULL REFERENCES carts(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        NUMERIC(12,3) NOT NULL,
    UNIQUE (cart_id, product_id)
);

-- =============================================================================
-- Outbox и подписки уведомлений
-- =============================================================================

CREATE TABLE outbox_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type   TEXT NOT NULL,
    aggregate_id     UUID NOT NULL,
    type             TEXT NOT NULL,
    payload          JSONB NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts         INT NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_messages (status, next_attempt_at);

CREATE TABLE notification_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('TELEGRAM','EMAIL')),
    destination     TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, channel, destination)
);

-- =============================================================================
-- Документы: счета, ТН/ТТН, платежи
-- Снапшоты JSONB — копия данных на момент документа, после выпуска не меняются.
-- =============================================================================

CREATE TABLE invoices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number           TEXT NOT NULL,
    series           TEXT,
    issue_date       DATE NOT NULL,
    order_id         UUID NOT NULL REFERENCES orders(id),
    seller_snapshot  JSONB NOT NULL,
    buyer_snapshot   JSONB NOT NULL,
    total_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_vat        NUMERIC(12,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','CANCELLED')),
    pdf_file_id      UUID REFERENCES files(id),
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoice_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoices(id),
    product_snapshot JSONB NOT NULL,
    quantity         NUMERIC(12,3) NOT NULL,
    unit_price       NUMERIC(12,2) NOT NULL,
    vat_rate         NUMERIC(4,2) NOT NULL,
    total            NUMERIC(12,2) NOT NULL
);

CREATE TABLE waybills (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type             VARCHAR(5) NOT NULL CHECK (type IN ('TN','TTN')),
    number           TEXT NOT NULL,
    series           TEXT,
    issue_date       DATE NOT NULL,
    invoice_id       UUID REFERENCES invoices(id),
    order_id         UUID NOT NULL REFERENCES orders(id),
    seller_snapshot  JSONB NOT NULL,
    buyer_snapshot   JSONB NOT NULL,
    carrier_snapshot JSONB,
    pdf_file_id      UUID REFERENCES files(id),
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE waybill_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    waybill_id       UUID NOT NULL REFERENCES waybills(id),
    product_snapshot JSONB NOT NULL,
    quantity         NUMERIC(12,3) NOT NULL,
    unit_price       NUMERIC(12,2) NOT NULL,
    vat_rate         NUMERIC(4,2) NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    weight           NUMERIC(12,3)
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id),
    amount          NUMERIC(12,2) NOT NULL,
    paid_at         DATE NOT NULL,
    method          TEXT,
    note            TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- CRM
-- =============================================================================

CREATE TABLE leads (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source               TEXT,
    name                 TEXT NOT NULL,
    phone                TEXT,
    email                TEXT,
    company_name         TEXT,
    message              TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'NEW',
    assigned_manager_id  UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE crm_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignee_id     UUID NOT NULL REFERENCES users(id),
    company_id      UUID REFERENCES companies(id),
    title           TEXT NOT NULL,
    description     TEXT,
    due_date        TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE crm_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    author_id       UUID NOT NULL REFERENCES users(id),
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- Заявки с сайта, CMS, аудит, обмен с 1С, настройки
-- =============================================================================

CREATE TABLE site_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    phone           TEXT,
    email           TEXT,
    message         TEXT,
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE articles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            TEXT NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at    TIMESTAMPTZ,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users(id),
    action          TEXT NOT NULL,
    target_type     TEXT,
    target_id       UUID,
    before          JSONB,
    after           JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

CREATE TABLE integration_files (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direction           VARCHAR(10) NOT NULL CHECK (direction IN ('IMPORT','EXPORT')),
    type                VARCHAR(30) NOT NULL
                        CHECK (type IN ('PRICES','STOCK','ORDERS','INVOICES','WAYBILLS')),
    file_id             UUID NOT NULL REFERENCES files(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rows_total          INT,
    rows_ok             INT,
    rows_error          INT,
    error_log_file_id   UUID REFERENCES files(id),
    processed_by        UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);

CREATE TABLE settings (
    key             TEXT PRIMARY KEY,
    value           JSONB NOT NULL
);

-- =============================================================================
-- Индексы на внешние ключи (основные пути выборок)
-- =============================================================================

CREATE INDEX idx_users_company_id ON users (company_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_product_prices_product_id ON product_prices (product_id);
CREATE INDEX idx_customer_prices_company_id ON customer_prices (company_id);
CREATE INDEX idx_customer_discounts_company_id ON customer_discounts (company_id);
CREATE INDEX idx_orders_customer_company_id ON orders (customer_company_id);
CREATE INDEX idx_orders_customer_user_id ON orders (customer_user_id);
CREATE INDEX idx_orders_manager_id ON orders (manager_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_purchase_order_items_po_id ON purchase_order_items (purchase_order_id);
CREATE INDEX idx_warehouse_document_items_document_id ON warehouse_document_items (document_id);
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX idx_invoice_items_invoice_id ON invoice_items (invoice_id);
CREATE INDEX idx_waybill_items_waybill_id ON waybill_items (waybill_id);
CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);
CREATE INDEX idx_leads_assigned_manager_id ON leads (assigned_manager_id);
CREATE INDEX idx_crm_tasks_assignee_id ON crm_tasks (assignee_id);
CREATE INDEX idx_crm_notes_company_id ON crm_notes (company_id);
