-- V12__add_session_token_to_analytics.sql
ALTER TABLE portfolio.portfolio_analytics
    ADD COLUMN IF NOT EXISTS session_token VARCHAR(36);