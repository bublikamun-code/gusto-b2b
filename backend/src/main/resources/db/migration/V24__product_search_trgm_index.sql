-- Аудит 2026-09-30, группа «Витрина».
--
-- Поиск по витрине идёт как lower(name) LIKE '%...%'. Такой предикат не использует
-- обычный индекс B-tree даже при lower(name) — ведущий '%' исключает его всегда,
-- а functional-индекс на lower(name) в V7/V11 тоже бесполезен именно из-за '%'.
--
-- Решение — трёхграммный (pg_trgm) индекс: он ускоряет LIKE '%...%' иILIKE по
-- тексту, и это единственный способ сделать такой поиск индексируемым.
--
-- Публичная (permitAll) ручка /api/v1/catalog/products с поиском иначе делала
-- seq scan по всей таблице products на каждом запросе.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- lower(name) совпадает с предикатом в ProductSpecification
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING gin (lower(name) gin_trgm_ops);

-- Сопутствующие фильтры витрины: category_id / brand_id / is_active часто идут
-- вместе с поиском, а is_active в явном B-tree индексе не было вовсе.
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_brand_id ON products (brand_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products (is_active);