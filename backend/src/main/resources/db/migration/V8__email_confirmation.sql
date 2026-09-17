-- V8: S08.1 — подтверждение email при саморегистрации физлица.
-- Поведение гейтится настройкой auth.require_email_confirmation (по умолчанию false),
-- включается на запуске вместе с боевым SMTP (S42).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_confirmed_at TIMESTAMPTZ NULL;

CREATE TABLE email_confirmation_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_confirmation_tokens_user_id ON email_confirmation_tokens (user_id);
CREATE INDEX idx_email_confirmation_tokens_expires_at ON email_confirmation_tokens (expires_at);

INSERT INTO settings (key, value) VALUES ('auth.require_email_confirmation', 'false')
ON CONFLICT (key) DO NOTHING;
