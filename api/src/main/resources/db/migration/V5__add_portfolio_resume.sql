-- V5__add_portfolio_resume.sql

-- Separate table for resume binary data
-- Kept separate so portfolio queries never load the blob
CREATE TABLE portfolio.portfolio_resumes (
   id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   portfolio_id UUID NOT NULL REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
   file_name    VARCHAR(255) NOT NULL,
   file_size    INTEGER NOT NULL,
   mime_type    VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
   file_data    BYTEA NOT NULL,
   uploaded_at  TIMESTAMP NOT NULL DEFAULT now(),
   UNIQUE (portfolio_id)
);

CREATE INDEX idx_resume_portfolio_id ON portfolio.portfolio_resumes(portfolio_id);

-- Add resume metadata to portfolios table
-- Only the name is stored here so portfolio fetch never touches the blob
ALTER TABLE portfolio.portfolios
    ADD COLUMN IF NOT EXISTS resume_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resume_file_size INTEGER;