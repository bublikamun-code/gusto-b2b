-- V16: S26 — ТН/ТТН: нумерация с серией (2.2) и уникальность номера.
-- Серии берутся из settings ('document.series.tn'/'document.series.ttn', seed: "A").
-- При смене серии/года sequence создаётся задачей ротации (S31); сервис страхуется
-- CREATE SEQUENCE IF NOT EXISTS.

CREATE SEQUENCE IF NOT EXISTS doc_seq_tn_A_2026;
CREATE SEQUENCE IF NOT EXISTS doc_seq_ttn_A_2026;

CREATE UNIQUE INDEX IF NOT EXISTS ux_waybills_number ON waybills (number);
CREATE INDEX IF NOT EXISTS idx_waybills_order ON waybills (order_id);
