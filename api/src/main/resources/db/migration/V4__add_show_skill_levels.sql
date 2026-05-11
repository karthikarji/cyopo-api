-- V4__add_show_skill_levels_to_portfolio_settings.sql

ALTER TABLE portfolio.portfolios
    ADD COLUMN IF NOT EXISTS settings_show_skill_levels BOOLEAN NOT NULL DEFAULT TRUE;