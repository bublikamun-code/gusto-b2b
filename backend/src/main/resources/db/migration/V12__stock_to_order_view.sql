-- V12: S18.3 — отчёт «что заказать»: активные товары ниже min_stock
-- по суммарному доступному остатку. Sequences TRANSFER/INVENTORY уже в V10.

CREATE VIEW v_stock_to_order AS
SELECT p.id                                   AS product_id,
       p.sku,
       p.name                                 AS product_name,
       p.min_stock,
       COALESCE(SUM(b.quantity - b.reserved), 0) AS available
FROM products p
LEFT JOIN stock_balances b ON b.product_id = p.id
WHERE p.deleted_at IS NULL
  AND p.is_active = TRUE
  AND p.min_stock > 0
GROUP BY p.id, p.sku, p.name, p.min_stock
HAVING COALESCE(SUM(b.quantity - b.reserved), 0) < p.min_stock;
