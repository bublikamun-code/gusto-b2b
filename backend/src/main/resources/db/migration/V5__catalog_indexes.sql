-- V5: Каталог — индексы, связь product ↔ brand и аудит-даты (S13)

-- Связь товара с брендом (необходима для скидок по бренду в S15)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS brand_id UUID REFERENCES brands(id);

-- Аудит-даты, используемые JPA @CreationTimestamp / @UpdateTimestamp
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_products_brand_id ON products(brand_id);

-- Полнотекстовый поиск по названию товара через pg_trgm
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products
    USING gin(name gin_trgm_ops);
