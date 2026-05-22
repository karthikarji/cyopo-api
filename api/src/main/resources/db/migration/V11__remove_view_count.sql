-- V11__remove_view_count.sql

ALTER TABLE portfolio.portfolios
DROP COLUMN IF EXISTS view_count;