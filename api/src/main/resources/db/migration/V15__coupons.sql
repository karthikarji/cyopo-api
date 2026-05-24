CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.coupons (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50)    NOT NULL UNIQUE,
    description    VARCHAR(255),
    discount_type  VARCHAR(20)    NOT NULL, -- PERCENTAGE, FIXED, FULL
    discount_value DECIMAL(10,2),
    max_uses       INTEGER,                 -- null = unlimited
    used_count     INTEGER        NOT NULL DEFAULT 0,
    per_user_limit INTEGER        NOT NULL DEFAULT 1,
    valid_from     TIMESTAMP,
    valid_until    TIMESTAMP,
    is_active      BOOLEAN        NOT NULL DEFAULT true,
    created_by     UUID REFERENCES auth.users(id),
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE billing.coupon_targets (
    coupon_id UUID NOT NULL REFERENCES billing.coupons(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    PRIMARY KEY (coupon_id, user_id)
);

CREATE TABLE billing.coupon_redemptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id   UUID NOT NULL REFERENCES billing.coupons(id),
    user_id     UUID NOT NULL REFERENCES auth.users(id),
    redeemed_at TIMESTAMP NOT NULL DEFAULT now(),
    plan_before VARCHAR(20),
    plan_after  VARCHAR(20)
);

CREATE INDEX idx_coupons_code        ON billing.coupons(code);
CREATE INDEX idx_redemptions_coupon  ON billing.coupon_redemptions(coupon_id);
CREATE INDEX idx_redemptions_user    ON billing.coupon_redemptions(user_id);