CREATE TABLE portfolio.portfolio_educations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id   UUID NOT NULL REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
    institution    VARCHAR(255) NOT NULL,
    degree         VARCHAR(255) NOT NULL,
    field          VARCHAR(255),
    start_date     VARCHAR(10),
    end_date       VARCHAR(10),
    is_current     BOOLEAN NOT NULL DEFAULT false,
    grade          VARCHAR(50),
    description    TEXT,
    sort_order     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_education_portfolio_id
    ON portfolio.portfolio_educations(portfolio_id);