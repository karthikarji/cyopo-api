-- V13__settings.sql

-- Notification preferences on users table
ALTER TABLE auth.users
    ADD COLUMN IF NOT EXISTS notification_preferences JSONB
    NOT NULL DEFAULT '{"emailOnMessage": true, "weeklyDigest": true}';

-- Password reset tokens table
CREATE TABLE auth.password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_token
    ON auth.password_reset_tokens(token);