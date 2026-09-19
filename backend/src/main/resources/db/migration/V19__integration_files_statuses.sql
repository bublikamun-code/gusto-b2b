-- V19: S35 — статусы integration_files по словарю 2.8
-- (UPLOADED/PROCESSING/DONE/FAILED вместо V1 PENDING/SENT/FAILED).

ALTER TABLE integration_files DROP CONSTRAINT IF EXISTS integration_files_status_check;
ALTER TABLE integration_files DROP CONSTRAINT IF EXISTS chk_integration_files_status;
ALTER TABLE integration_files ADD CONSTRAINT chk_integration_files_status
    CHECK (status IN ('UPLOADED','PROCESSING','DONE','FAILED'));
ALTER TABLE integration_files ALTER COLUMN status SET DEFAULT 'UPLOADED';

CREATE INDEX IF NOT EXISTS idx_integration_files_type ON integration_files (type, created_at);
