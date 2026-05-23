-- V14
ALTER TABLE template.templates
    ADD COLUMN IF NOT EXISTS thumbnail_public_id VARCHAR(255);