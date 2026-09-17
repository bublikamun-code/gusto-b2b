-- V10: S18.1 — нумерация складских документов (2.2): <ТИП>-<порядковый>.
-- Sequence на (тип, год); ротация на новый год — задачей планировщика (S31).

CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_incoming_2026;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_outgoing_2026;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_write_off_2026;
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_transfer_2026;   -- S18.3
CREATE SEQUENCE IF NOT EXISTS doc_seq_warehouse_inventory_2026;  -- S18.3
