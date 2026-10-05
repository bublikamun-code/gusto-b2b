-- Аудит 2026-09-30, P1-20/P1-21. Существующие миграции не переписываются — только новый файл.
--
-- 1) Sequences на следующий год. V10/V11/V14/V15/V16 создавали sequence строго на 2026 год,
--    а SequenceRotationJob крутился раз в сутки в 00:05 и покрывал только invoice/tn/ttn.
--    С 01.01.2027 создание заказа, заказа поставщику и складского документа падало бы в 500.
--    Job теперь создаёт все семейства сам, а эта миграция закрывает окно «деплой до 00:05».
-- 2) Сидовый розничный прайс-лист истекал 2026-12-31: с этого момента RetailPriceService
--    возвращал пусто, витрина показывала «0,00 р.», а корзина отвечала «Для товара X не задана цена».
--    Продлеваем до конца 2027-го — демо-каталог перестаёт «протухать» на новый год.

CREATE SEQUENCE IF NOT EXISTS order_seq_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_purchase_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_invoice_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_tn_A_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_ttn_A_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_incoming_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_outgoing_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_write_off_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_transfer_2027;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_inventory_2027;

UPDATE price_lists
SET valid_to = DATE '2027-12-31'
WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid
  AND valid_to < DATE '2027-12-31';

UPDATE product_prices pp
SET valid_to = DATE '2027-12-31'
FROM price_lists pl
WHERE pl.id = pp.price_list_id
  AND pl.id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid
  AND pp.valid_to < DATE '2027-12-31';