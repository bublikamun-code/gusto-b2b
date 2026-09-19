-- V18: S31 — заявки с сайта: CHECK-ограничения словаря 2.8.

ALTER TABLE site_requests DROP CONSTRAINT IF EXISTS chk_site_requests_type;
ALTER TABLE site_requests ADD CONSTRAINT chk_site_requests_type
    CHECK (type IN ('CALLBACK','WHOLESALE','RETAIL','OTHER'));

ALTER TABLE site_requests DROP CONSTRAINT IF EXISTS chk_site_requests_status;
ALTER TABLE site_requests ADD CONSTRAINT chk_site_requests_status
    CHECK (status IN ('NEW','IN_PROGRESS','CLOSED'));

CREATE INDEX IF NOT EXISTS idx_site_requests_status ON site_requests (status, created_at);

-- Связь заявки с лидом (S31: заявка создаёт лид в пул «не назначено», 2.7)
ALTER TABLE site_requests ADD COLUMN IF NOT EXISTS lead_id UUID NULL REFERENCES leads(id);

-- Связь заявки с лидом (S31: заявка создаёт лид в пул «не назначено», 2.7)
ALTER TABLE site_requests ADD COLUMN IF NOT EXISTS lead_id UUID NULL REFERENCES leads(id);
