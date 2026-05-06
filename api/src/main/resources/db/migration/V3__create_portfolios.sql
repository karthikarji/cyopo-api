CREATE SCHEMA IF NOT EXISTS portfolio;

CREATE TABLE portfolio.portfolios (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      user_id UUID NOT NULL,
                                      template_id UUID NOT NULL,
                                      name VARCHAR(255) NOT NULL,
                                      slug VARCHAR(50) NOT NULL UNIQUE,
                                      status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                                      view_count BIGINT NOT NULL DEFAULT 0,

    -- PortfolioProfile (embedded)
                                      profile_name VARCHAR(100),
                                      profile_title VARCHAR(200),
                                      profile_bio VARCHAR(1000),
                                      profile_email VARCHAR(255),
                                      profile_phone VARCHAR(50),
                                      profile_location VARCHAR(100),
                                      profile_website VARCHAR(500),
                                      profile_photo VARCHAR(500),
                                      profile_status VARCHAR(20) DEFAULT 'DRAFT',

    -- PortfolioSettings (embedded)
                                      settings_is_public BOOLEAN DEFAULT FALSE,
                                      settings_allow_comments BOOLEAN DEFAULT TRUE,
                                      settings_show_contact_info BOOLEAN DEFAULT TRUE,
                                      settings_custom_domain VARCHAR(255),
                                      settings_seo_title VARCHAR(60),
                                      settings_seo_description VARCHAR(160),

                                      template_config JSONB,
                                      created_at TIMESTAMP NOT NULL DEFAULT now(),
                                      updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE portfolio.portfolio_social_links (
                                                  portfolio_id UUID NOT NULL
                                                      REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                                  platform VARCHAR(100),
                                                  url VARCHAR(500),
                                                  username VARCHAR(100)
);

CREATE TABLE portfolio.portfolio_skills (
                                            portfolio_id UUID NOT NULL
                                                REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                            name VARCHAR(100),
                                            category VARCHAR(50),
                                            proficiency VARCHAR(50),
                                            level INT
);

CREATE TABLE portfolio.portfolio_certifications (
                                                    portfolio_id UUID NOT NULL
                                                        REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                                    name VARCHAR(255),
                                                    provider VARCHAR(255),
                                                    issue_date DATE,
                                                    expiry_date DATE,
                                                    credential_id VARCHAR(255),
                                                    credential_url VARCHAR(500)
);

CREATE TABLE portfolio.experiences (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       portfolio_id UUID NOT NULL
                                           REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                       title VARCHAR(255) NOT NULL,
                                       company VARCHAR(255) NOT NULL,
                                       location VARCHAR(255),
                                       start_date DATE,
                                       end_date DATE,
                                       is_current BOOLEAN DEFAULT FALSE,
                                       description TEXT NOT NULL,
                                       achievements JSONB,
                                       technologies JSONB
);

CREATE TABLE portfolio.projects (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    portfolio_id UUID NOT NULL
                                        REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                    title VARCHAR(255) NOT NULL,
                                    description TEXT NOT NULL,
                                    thumbnail VARCHAR(500),
                                    demo_url VARCHAR(500),
                                    github_url VARCHAR(500),
                                    is_featured BOOLEAN DEFAULT FALSE,
                                    completed_date DATE,
                                    technologies JSONB
);

CREATE TABLE portfolio.contacts (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    portfolio_id UUID NOT NULL
                                        REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                    name VARCHAR(50) NOT NULL,
                                    email VARCHAR(255) NOT NULL,
                                    subject VARCHAR(100) NOT NULL,
                                    message VARCHAR(1000) NOT NULL,
                                    status VARCHAR(20) DEFAULT 'UNREAD',
                                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                                    UNIQUE (portfolio_id, email)
);

CREATE TABLE portfolio.portfolio_analytics (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               portfolio_id UUID NOT NULL
                                                   REFERENCES portfolio.portfolios(id) ON DELETE CASCADE,
                                               owner_id UUID NOT NULL,
                                               viewer_user_id UUID,
                                               viewer_ip VARCHAR(45),
                                               created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_portfolios_user ON portfolio.portfolios(user_id);
CREATE INDEX idx_portfolios_slug ON portfolio.portfolios(slug);
CREATE INDEX idx_portfolios_status ON portfolio.portfolios(status);
CREATE INDEX idx_portfolios_public
    ON portfolio.portfolios(settings_is_public);
CREATE INDEX idx_analytics_portfolio
    ON portfolio.portfolio_analytics(portfolio_id);
CREATE INDEX idx_analytics_owner
    ON portfolio.portfolio_analytics(owner_id);
CREATE INDEX idx_analytics_created
    ON portfolio.portfolio_analytics(created_at);
CREATE INDEX idx_contacts_portfolio
    ON portfolio.contacts(portfolio_id);