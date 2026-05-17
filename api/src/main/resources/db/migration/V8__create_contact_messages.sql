--V8__create_contact_message.sql

CREATE TABLE portfolio.contact_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
    portfolio_slug VARCHAR(50) NOT NULL,
    name         VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    message      TEXT NOT NULL,
    ip_address   VARCHAR(45),
    sent_at      TIMESTAMP NOT NULL DEFAULT now()
);