-- Profile photo public ID for deletion
ALTER TABLE portfolio.portfolios
    ADD COLUMN IF NOT EXISTS profile_photo_public_id VARCHAR(255);

-- Resume table — add Cloudinary fields
ALTER TABLE portfolio.portfolio_resumes
    ADD COLUMN IF NOT EXISTS file_url        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cloud_public_id VARCHAR(255);

-- Projects — add public ID for thumbnail deletion
ALTER TABLE portfolio.projects
    ADD COLUMN IF NOT EXISTS thumbnail_public_id VARCHAR(255);


-- Project photos table — multiple photos per project
CREATE TABLE portfolio.project_photos (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES portfolio.projects(id) ON DELETE CASCADE,
    file_url     VARCHAR(500) NOT NULL,
    public_id    VARCHAR(255) NOT NULL,
    file_name    VARCHAR(255),
    file_size    INTEGER,
    is_thumbnail BOOLEAN NOT NULL DEFAULT false,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    uploaded_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_photos_project_id
    ON portfolio.project_photos(project_id);

ALTER TABLE portfolio.portfolio_resumes
DROP COLUMN IF EXISTS file_data;