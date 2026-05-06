CREATE SCHEMA IF NOT EXISTS template;

CREATE TABLE template.templates (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    title VARCHAR(100) NOT NULL UNIQUE,
                                    description VARCHAR(1000) NOT NULL,
                                    thumbnail VARCHAR(500) NOT NULL,
                                    font VARCHAR(100) NOT NULL,
                                    primary_color VARCHAR(7) NOT NULL,
                                    secondary_color VARCHAR(7) NOT NULL,
                                    premium BOOLEAN NOT NULL DEFAULT FALSE,
                                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                                    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE template.template_tags (
                                        template_id UUID NOT NULL
                                            REFERENCES template.templates(id) ON DELETE CASCADE,
                                        tag VARCHAR(50) NOT NULL
);

CREATE INDEX idx_templates_status
    ON template.templates(status);
CREATE INDEX idx_templates_premium
    ON template.templates(premium);
CREATE INDEX idx_templates_created
    ON template.templates(created_at DESC);
CREATE INDEX idx_template_tags_template
    ON template.template_tags(template_id);
CREATE INDEX idx_template_tags_tag
    ON template.template_tags(tag);
CREATE INDEX idx_templates_search
    ON template.templates
    USING GIN (to_tsvector('english', title || ' ' || description));