-- V7__add_template_slug.sql

ALTER TABLE template.templates
    ADD COLUMN IF NOT EXISTS slug VARCHAR(10) UNIQUE;

UPDATE template.templates SET slug = 'MLT' WHERE title = 'minimal light updated (copy)';
UPDATE template.templates SET slug = 'BCT' WHERE title = 'bold creative';
UPDATE template.templates SET slug = 'DPT' WHERE title = 'dark professional';
UPDATE template.templates SET slug = 'MGT' WHERE title = 'modern gradient';

ALTER TABLE template.templates
    ALTER COLUMN slug SET NOT NULL;

-- Add template_slug to portfolios table
ALTER TABLE portfolio.portfolios
    ADD COLUMN IF NOT EXISTS template_slug VARCHAR(10);

-- Backfill existing portfolios
UPDATE portfolio.portfolios p
SET template_slug = t.slug
    FROM template.templates t
WHERE p.template_id = t.id;