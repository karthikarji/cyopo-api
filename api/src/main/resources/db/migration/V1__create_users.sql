CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            email VARCHAR(255) NOT NULL UNIQUE,
                            password VARCHAR(255),
                            name VARCHAR(50),
                            role VARCHAR(20) NOT NULL DEFAULT 'USER',
                            plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
                            status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                            upgraded_at TIMESTAMP,
                            subscription_status VARCHAR(20),
                            subscription_plan VARCHAR(20),
                            subscription_period_start TIMESTAMP,
                            subscription_period_end TIMESTAMP,
                            created_at TIMESTAMP NOT NULL DEFAULT now(),
                            updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_role ON auth.users(role);
CREATE INDEX idx_users_plan ON auth.users(plan);
CREATE INDEX idx_users_status ON auth.users(status);

CREATE TABLE auth.refresh_tokens (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
                                     token VARCHAR(500) NOT NULL UNIQUE,
                                     expires_at TIMESTAMP NOT NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token ON auth.refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON auth.refresh_tokens(expires_at);