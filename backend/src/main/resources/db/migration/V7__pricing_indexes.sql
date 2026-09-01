-- V7: индексы для ценообразования (S15)
-- Ускоряют поиск актуальных персональных цен, скидок и базовых цен.

CREATE INDEX IF NOT EXISTS idx_customer_prices_company_product_valid
    ON customer_prices (company_id, product_id, valid_from);

CREATE INDEX IF NOT EXISTS idx_customer_discounts_company_brand_valid
    ON customer_discounts (company_id, brand_id, valid_from);

CREATE INDEX IF NOT EXISTS idx_customer_discounts_company_category_valid
    ON customer_discounts (company_id, category_id, valid_from);

CREATE INDEX IF NOT EXISTS idx_product_prices_price_list_product_valid
    ON product_prices (price_list_id, product_id, valid_from);
