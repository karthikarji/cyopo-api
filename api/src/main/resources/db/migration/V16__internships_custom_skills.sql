-- Add experience type (FULL_TIME, PART_TIME, INTERNSHIP, FREELANCE, CONTRACT)
ALTER TABLE portfolio.experiences
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME';

-- Add custom skill categories per portfolio
ALTER TABLE portfolio.portfolios
    ADD COLUMN custom_skill_categories JSONB NOT NULL DEFAULT '[]';

-- Add custom category per skill
ALTER TABLE portfolio.portfolio_skills
    ADD COLUMN custom_category VARCHAR(100);