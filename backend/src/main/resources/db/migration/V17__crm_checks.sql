-- V17: S29 — CRM: CHECK-ограничения словаря статусов (2.8) на уже существующие таблицы.

ALTER TABLE leads DROP CONSTRAINT IF EXISTS chk_leads_status;
ALTER TABLE leads ADD CONSTRAINT chk_leads_status
    CHECK (status IN ('NEW','IN_PROGRESS','QUALIFIED','WON','LOST'));

ALTER TABLE crm_tasks DROP CONSTRAINT IF EXISTS chk_crm_tasks_status;
ALTER TABLE crm_tasks ADD CONSTRAINT chk_crm_tasks_status
    CHECK (status IN ('OPEN','DONE','CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_leads_status ON leads (status);
CREATE INDEX IF NOT EXISTS idx_leads_manager ON leads (assigned_manager_id);
CREATE INDEX IF NOT EXISTS idx_crm_tasks_assignee ON crm_tasks (assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_crm_notes_company ON crm_notes (company_id, created_at);
