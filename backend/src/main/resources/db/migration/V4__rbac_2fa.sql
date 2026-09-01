-- V4: RBAC + 2FA TOTP (S09)
-- Роли уже ограничены CHECK в users.role (V1).

-- 2FA: флаг включения (секрет totp_secret уже есть в V1)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Recovery-коды для TOTP (показываются один раз при включении 2FA)
CREATE TABLE recovery_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recovery_codes_user_id ON recovery_codes(user_id);
