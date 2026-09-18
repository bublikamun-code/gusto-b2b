-- V13: S19.1 — витрина: флаги «Хит недели» и «Новинка» (управляются из админки).
-- weight_per_unit (шаг весового товара) уже есть с V1.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS is_hit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_new BOOLEAN NOT NULL DEFAULT FALSE;

-- Демо: пара хитов и новинка для витрины (заменяются из админки)
UPDATE products SET is_hit = TRUE WHERE sku IN ('steyk-ribay', 'bedro-kurinoye');
UPDATE products SET is_new = TRUE WHERE sku = 'kolbaski-dlya-zharki';
UPDATE products SET weight_per_unit = 0.5 WHERE sku IN ('farsh-govyazhiy', 'farsh-kuriniy');
