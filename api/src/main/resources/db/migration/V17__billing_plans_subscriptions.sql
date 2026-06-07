-- ============================================================
-- V17__billing_plans_subscriptions.sql
-- Billing: plans, plan_prices, subscriptions, payments,
--          payment_attempts, invoices, webhook_events,
--          plan_change_log
-- ============================================================

-- ─── Plans ────────────────────────────────────────────────────────
CREATE TABLE billing.plans (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    name                        VARCHAR(20) NOT NULL UNIQUE,  -- FREE | PREMIUM | PRO
    display_name                VARCHAR(50) NOT NULL,
    description                 TEXT,

    -- Hard limits (enforced by backend)
    max_portfolios              INTEGER     NOT NULL DEFAULT 1,
    max_projects_per_portfolio  INTEGER     NOT NULL DEFAULT 3,
    allow_custom_domain         BOOLEAN     NOT NULL DEFAULT false,
    allow_resume_upload         BOOLEAN     NOT NULL DEFAULT true,
    allow_analytics             BOOLEAN     NOT NULL DEFAULT true,
    allow_premium_templates     BOOLEAN     NOT NULL DEFAULT false,
    allow_messages              BOOLEAN     NOT NULL DEFAULT true,
    remove_branding             BOOLEAN     NOT NULL DEFAULT false,

    -- UI — bullet points on pricing card (JSON array of strings)
    features                    JSONB       NOT NULL DEFAULT '[]',

    -- Pricing card badge
    badge                       VARCHAR(50),  -- "Most Popular" | "Best Value" | null

    -- Display order on pricing page
    sort_order                  INTEGER     NOT NULL DEFAULT 0,

    -- Soft delete / hide without deleting
    is_active                   BOOLEAN     NOT NULL DEFAULT true,
    created_at                  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP   NOT NULL DEFAULT now()
);

-- ─── Plan prices (one row per plan per currency) ───────────────────
CREATE TABLE billing.plan_prices (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID         NOT NULL REFERENCES billing.plans(id) ON DELETE CASCADE,

    -- Currency + gateway
    currency        VARCHAR(3)   NOT NULL,   -- INR | USD | GBP | EUR | SGD
    gateway         VARCHAR(20)  NOT NULL,   -- RAZORPAY | STRIPE

    -- Prices stored in smallest unit
    -- INR: paise (49900 = ₹499)
    -- USD: cents (600 = $6)
    -- GBP: pence (500 = £5)
    monthly_price   BIGINT       NOT NULL DEFAULT 0,
    annual_price    BIGINT       NOT NULL DEFAULT 0,

    -- GST / tax rate (only applicable for INR / India)
    gst_rate        DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    -- Can be deactivated without deleting
    -- e.g. Stripe rows inactive until Stripe goes live
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),

    UNIQUE (plan_id, currency)
);

CREATE INDEX idx_plan_prices_plan     ON billing.plan_prices(plan_id);
CREATE INDEX idx_plan_prices_currency ON billing.plan_prices(currency);

-- ─── Orders ───────────────────────────────────────────────────────
-- Represents a checkout session / purchase intent
-- Created before payment, referenced throughout the flow
CREATE TABLE billing.orders (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES auth.users(id),
    plan_id             UUID        NOT NULL REFERENCES billing.plans(id),
    plan_price_id       UUID        NOT NULL REFERENCES billing.plan_prices(id),

    -- Billing cycle
    -- MONTHLY | ANNUAL
    billing_cycle       VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',

    -- Gateway
    gateway             VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id    VARCHAR(100) UNIQUE,  -- Razorpay order_id

    -- Status
    -- PENDING   → created, awaiting payment
    -- PAID      → payment captured successfully
    -- FAILED    → payment failed
    -- EXPIRED   → user abandoned, order expired (15 mins)
    -- CANCELLED → user cancelled at checkout
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Coupon applied at checkout
    coupon_id           UUID        REFERENCES billing.coupons(id),

    -- Currency
    currency            VARCHAR(3)  NOT NULL DEFAULT 'INR',

    -- Amounts (smallest unit — calculated at order creation)
    plan_price          BIGINT      NOT NULL DEFAULT 0,  -- base price
    discount_amount     BIGINT      NOT NULL DEFAULT 0,  -- coupon discount
    subtotal            BIGINT      NOT NULL DEFAULT 0,  -- after discount
    gst_rate            DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    gst_amount          BIGINT      NOT NULL DEFAULT 0,
    total_amount        BIGINT      NOT NULL DEFAULT 0,  -- final amount charged

    -- Idempotency — prevents creating duplicate orders
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,

    -- Location at time of order creation
    country_code        VARCHAR(3),
    ip_address          VARCHAR(45),

    -- GST
    gstin               VARCHAR(15), -- buyer's GSTIN (optional)

    -- subscription_id set after successful payment + activation
    subscription_id     UUID,        -- FK added via ALTER after subscriptions created

    -- Order expiry (PENDING orders expire after 15 mins if not paid)
    expires_at          TIMESTAMP   NOT NULL DEFAULT (now() + INTERVAL '15 minutes'),

    -- Failure info
    failure_code        VARCHAR(100),
    failure_description TEXT,

    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user       ON billing.orders(user_id);
CREATE INDEX idx_orders_status     ON billing.orders(status);
CREATE INDEX idx_orders_gateway    ON billing.orders(gateway_order_id);
CREATE INDEX idx_orders_expires    ON billing.orders(expires_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_orders_idempotency ON billing.orders(idempotency_key);


-- ─── Payments ─────────────────────────────────────────────────────
CREATE TABLE billing.payments (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL REFERENCES auth.users(id),

    -- subscription_id set after activation (FK added via ALTER)
    subscription_id         UUID,

    -- Add to billing.payments:
    order_id                UUID        REFERENCES billing.orders(id),

    -- Gateway
    gateway                 VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id        VARCHAR(100) UNIQUE,   -- Razorpay order_id
    gateway_payment_id      VARCHAR(100) UNIQUE,   -- Razorpay payment_id

    -- Idempotency — prevents double charges
    idempotency_key         VARCHAR(100) NOT NULL UNIQUE,

    -- Status
    -- CREATED | ATTEMPTED | CAPTURED | FAILED | EXPIRED | REFUNDED | PARTIALLY_REFUNDED
    status                  VARCHAR(30) NOT NULL DEFAULT 'CREATED',

    -- Currency
    currency                VARCHAR(3)  NOT NULL DEFAULT 'INR',

    -- Amounts (smallest unit)
    subtotal_amount         BIGINT      NOT NULL DEFAULT 0,  -- before GST, before discount
    discount_amount         BIGINT      NOT NULL DEFAULT 0,
    gst_rate                DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    gst_amount              BIGINT      NOT NULL DEFAULT 0,
    total_amount            BIGINT      NOT NULL DEFAULT 0,  -- final charged amount
    refund_amount           BIGINT      NOT NULL DEFAULT 0,

    -- GST
    gstin                   VARCHAR(15),  -- buyer's GSTIN (B2B, optional)

    -- Failure tracking
    failure_code            VARCHAR(100),
    failure_description     TEXT,
    retry_count             INTEGER     NOT NULL DEFAULT 0,
    last_retry_at           TIMESTAMP,
    next_retry_at           TIMESTAMP,

    -- Location
    country_code            VARCHAR(3),   -- "IN" | "US" | "GB"
    payment_method          VARCHAR(50),  -- "upi" | "card" | "netbanking" | "wallet"

    -- IP address — masked after 30 days (GDPR compliance)
    ip_address              VARCHAR(45),
    ip_masked_at            TIMESTAMP,

    -- Refund
    refunded_at             TIMESTAMP,
    refund_gateway_id       VARCHAR(100),
    refund_reason           TEXT,

    -- Full gateway response (for debugging + reconciliation)
    gateway_response        JSONB,

    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_user            ON billing.payments(user_id);
CREATE INDEX idx_payments_order           ON billing.payments(order_id);
CREATE INDEX idx_payments_status          ON billing.payments(status);
CREATE INDEX idx_payments_gateway_order   ON billing.payments(gateway_order_id);
CREATE INDEX idx_payments_gateway_payment ON billing.payments(gateway_payment_id);
CREATE INDEX idx_payments_idempotency     ON billing.payments(idempotency_key);
CREATE INDEX idx_payments_created         ON billing.payments(created_at);

-- ─── Payment attempts ─────────────────────────────────────────────
-- Each individual try within a payment
-- Razorpay may have multiple attempts per order
CREATE TABLE billing.payment_attempts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID        NOT NULL REFERENCES billing.payments(id),
    gateway_payment_id  VARCHAR(100),
    amount              BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL DEFAULT 'INR',

    -- Add to billing.payment_attempts:
    order_id            UUID        REFERENCES billing.orders(id),

    -- ATTEMPTED | CAPTURED | FAILED
    status              VARCHAR(20) NOT NULL,

    failure_code        VARCHAR(100),
    failure_description TEXT,
    payment_method      VARCHAR(50),  -- upi | card | netbanking

    -- Full gateway response for this attempt
    gateway_response    JSONB,

    attempted_at        TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_attempts_payment ON billing.payment_attempts(payment_id);
CREATE INDEX idx_attempts_order   ON billing.payment_attempts(order_id);


-- ─── Subscriptions ────────────────────────────────────────────────
CREATE TABLE billing.subscriptions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    plan_id                 UUID        NOT NULL REFERENCES billing.plans(id),
    plan_price_id           UUID        REFERENCES billing.plan_prices(id),
    order_id                UUID        REFERENCES billing.orders(id),
    -- Gateway
    gateway                 VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    gateway_subscription_id VARCHAR(100),

    -- Status
    -- ACTIVE | CANCELLED | EXPIRED | PAST_DUE | PENDING
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Billing cycle
    -- MONTHLY | ANNUAL
    billing_cycle           VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',

    -- Current billing period
    current_period_start    TIMESTAMP   NOT NULL,
    current_period_end      TIMESTAMP   NOT NULL,

    -- Cancel at period end (Option B)
    -- true  → keep access until current_period_end, then downgrade
    -- false → active, will renew
    cancel_at_period_end    BOOLEAN     NOT NULL DEFAULT false,
    cancelled_at            TIMESTAMP,
    cancellation_reason     TEXT,

    -- Coupon applied
    coupon_id               UUID        REFERENCES billing.coupons(id),
    discount_amount         BIGINT      NOT NULL DEFAULT 0,

    -- Price snapshot at time of subscription
    -- Immune to future plan price changes
    currency                VARCHAR(3)  NOT NULL DEFAULT 'INR',
    plan_price              BIGINT      NOT NULL DEFAULT 0,  -- before GST
    gst_rate                DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    gst_amount              BIGINT      NOT NULL DEFAULT 0,
    final_amount            BIGINT      NOT NULL DEFAULT 0,  -- after GST, after discount

    -- Failed renewal tracking
    grace_period_end        TIMESTAMP,
    retry_count             INTEGER     NOT NULL DEFAULT 0,
    last_retry_at           TIMESTAMP,

    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_user     ON billing.subscriptions(user_id);
CREATE INDEX idx_subscriptions_status   ON billing.subscriptions(status);
CREATE INDEX idx_subscriptions_period   ON billing.subscriptions(current_period_end);



-- ─── Invoices ─────────────────────────────────────────────────────
CREATE TABLE billing.invoices (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES auth.users(id),
    order_id            UUID        REFERENCES billing.orders(id),
    payment_id          UUID        REFERENCES billing.payments(id),
    subscription_id     UUID        REFERENCES billing.subscriptions(id),

    -- Sequential invoice number per year: INV-2026-000001
    invoice_number      VARCHAR(30) NOT NULL UNIQUE,

    -- DRAFT | ISSUED | PAID | VOID
    status              VARCHAR(20) NOT NULL DEFAULT 'ISSUED',

    -- Currency
    currency            VARCHAR(3)  NOT NULL DEFAULT 'INR',

    -- Amounts (smallest unit)
    subtotal            BIGINT      NOT NULL DEFAULT 0,
    discount            BIGINT      NOT NULL DEFAULT 0,
    gst_rate            DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    gst_amount          BIGINT      NOT NULL DEFAULT 0,
    total               BIGINT      NOT NULL DEFAULT 0,

    -- Billing details snapshot at time of invoice
    billing_name        VARCHAR(255),
    billing_email       VARCHAR(255),
    billing_address     TEXT,

    -- GST details
    gstin               VARCHAR(15),  -- buyer's GSTIN (optional, B2B)
    seller_gstin        VARCHAR(15),  -- your company's GSTIN

    -- What period this invoice covers
    period_start        TIMESTAMP,
    period_end          TIMESTAMP,

    -- PDF stored in Cloudinary
    pdf_url             TEXT,
    pdf_public_id       VARCHAR(255),

    -- Dates
    issued_at           TIMESTAMP   NOT NULL DEFAULT now(),
    due_at              TIMESTAMP,
    paid_at             TIMESTAMP,

    created_at          TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoices_user   ON billing.invoices(user_id);
CREATE INDEX idx_invoices_order  ON billing.invoices(order_id);
CREATE INDEX idx_invoices_number ON billing.invoices(invoice_number);
CREATE INDEX idx_invoices_status ON billing.invoices(status);

-- ─── Invoice number sequence (per year) ───────────────────────────
CREATE SEQUENCE billing.invoice_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

-- ─── Webhook events (idempotency) ─────────────────────────────────
CREATE TABLE billing.webhook_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Gateway identifier
    gateway         VARCHAR(20) NOT NULL,   -- RAZORPAY | STRIPE

    -- Unique event ID from gateway — used for idempotency
    event_id        VARCHAR(100) NOT NULL,

    -- Event type e.g. payment.captured | payment.failed | refund.created
    event_type      VARCHAR(100) NOT NULL,

    -- Full raw payload from gateway
    payload         JSONB       NOT NULL,

    -- Processing state
    processed       BOOLEAN     NOT NULL DEFAULT false,
    processed_at    TIMESTAMP,

    -- Error tracking (for failed processing)
    error_message   TEXT,
    retry_count     INTEGER     NOT NULL DEFAULT 0,
    last_retry_at   TIMESTAMP,

    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    -- Idempotency — same event from same gateway cannot be processed twice
    UNIQUE (gateway, event_id)
);

CREATE INDEX idx_webhook_events_processed  ON billing.webhook_events(processed);
CREATE INDEX idx_webhook_events_type       ON billing.webhook_events(event_type);
CREATE INDEX idx_webhook_events_created    ON billing.webhook_events(created_at);

-- ─── Plan change audit log ────────────────────────────────────────
-- Full immutable audit trail of every plan change for every user
CREATE TABLE billing.plan_change_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES auth.users(id),

    from_plan       VARCHAR(20),  -- NULL on very first activation
    to_plan         VARCHAR(20)   NOT NULL,

    -- Why the plan changed
    -- PAYMENT | EXPIRY | ADMIN | REFUND | COUPON | CANCELLATION
    reason          VARCHAR(30)   NOT NULL,

    -- Context references
    subscription_id UUID        REFERENCES billing.subscriptions(id),
    payment_id      UUID        REFERENCES billing.payments(id),
    order_id        UUID        REFERENCES billing.orders(id),
    -- Who triggered the change
    -- NULL = automated system job
    -- UUID = admin who manually changed it
    changed_by      UUID        REFERENCES auth.users(id),

    changed_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_plan_log_user ON billing.plan_change_log(user_id);
CREATE INDEX idx_plan_log_date ON billing.plan_change_log(changed_at);


-- ─── Fix circular FK — add subscription_id FK to orders ───────────
-- Done via ALTER after subscriptions table exists
ALTER TABLE billing.orders
    ADD CONSTRAINT fk_orders_subscription
        FOREIGN KEY (subscription_id)
            REFERENCES billing.subscriptions(id);

-- ─── Fix circular FK — add subscription_id FK to payments ─────────
ALTER TABLE billing.payments
    ADD CONSTRAINT fk_payments_subscription
        FOREIGN KEY (subscription_id)
            REFERENCES billing.subscriptions(id);



-- ─── Seed plans ───────────────────────────────────────────────────
INSERT INTO billing.plans (
    name, display_name, description,
    max_portfolios, max_projects_per_portfolio,
    allow_custom_domain, allow_resume_upload,
    allow_analytics, allow_premium_templates,
    allow_messages, remove_branding,
    features, badge, sort_order
) VALUES
      (
          'FREE',
          'Free',
          'Perfect for getting started',
          1, 3,
          false, true, true, false, true, false,
          '[
            "1 portfolio",
            "Up to 3 projects",
            "Free templates only",
            "Contact messages",
            "Basic profile",
            "cyopo branding shown"
          ]'::jsonb,
          null,
          1
      ),
      (
          'PREMIUM',
          'Premium',
          'For professionals who want more',
          3, 2147483647,
          true, true, true, true, true, false,
          '[
            "Up to 3 portfolios",
            "Unlimited projects",
            "All templates including premium",
            "Custom domain",
            "Resume upload",
            "Full analytics dashboard",
            "Contact messages",
            "No cyopo branding"
          ]'::jsonb,
          'Most Popular',
          2
      ),
      (
          'PRO',
          'Pro',
          'For power users and agencies',
          2147483647, 2147483647,
          true, true, true, true, true, true,
          '[
            "Unlimited portfolios",
            "Unlimited projects",
            "All templates including premium",
            "Custom domain",
            "Resume upload",
            "Full analytics dashboard",
            "Priority support",
            "Remove cyopo branding",
            "Early access to new features"
          ]'::jsonb,
          'Best Value',
          3
      );

-- ─── Seed plan prices ─────────────────────────────────────────────

-- INR prices — Razorpay — ACTIVE
INSERT INTO billing.plan_prices
(plan_id, currency, gateway, monthly_price, annual_price, gst_rate, is_active)
SELECT
    id,
    'INR',
    'RAZORPAY',
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 49900
        WHEN 'PRO'     THEN 99900
        END,
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 499900
        WHEN 'PRO'     THEN 999900
        END,
    18.00,  -- 18% GST for India
    true
FROM billing.plans;

-- USD prices — Stripe — INACTIVE (until Stripe goes live)
INSERT INTO billing.plan_prices
(plan_id, currency, gateway, monthly_price, annual_price, gst_rate, is_active)
SELECT
    id,
    'USD',
    'STRIPE',
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 600
        WHEN 'PRO'     THEN 1200
        END,
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 5999
        WHEN 'PRO'     THEN 11999
        END,
    0.00,   -- no GST for USD
    false   -- inactive until Stripe is integrated
FROM billing.plans;

-- GBP prices — Stripe — INACTIVE
INSERT INTO billing.plan_prices
(plan_id, currency, gateway, monthly_price, annual_price, gst_rate, is_active)
SELECT
    id,
    'GBP',
    'STRIPE',
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 500
        WHEN 'PRO'     THEN 1000
        END,
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 4999
        WHEN 'PRO'     THEN 9999
        END,
    0.00,
    false
FROM billing.plans;

-- EUR prices — Stripe — INACTIVE
INSERT INTO billing.plan_prices
(plan_id, currency, gateway, monthly_price, annual_price, gst_rate, is_active)
SELECT
    id,
    'EUR',
    'STRIPE',
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 599
        WHEN 'PRO'     THEN 1099
        END,
    CASE name
        WHEN 'FREE'    THEN 0
        WHEN 'PREMIUM' THEN 5499
        WHEN 'PRO'     THEN 10999
        END,
    0.00,
    false
FROM billing.plans;



-- ─── Scheduler cron config reminder ──────────────────────────────
-- Add to application.yml:
--
-- app:
--   billing:
--     seller-gstin: "YOUR_GSTIN_HERE"
--     razorpay:
--       key-id:     RAZORPAY_KEY_ID
--       key-secret: RAZORPAY_KEY_SECRET
--       webhook-secret: RAZORPAY_WEBHOOK_SECRET
--   scheduler:
--     billing-expiry-cron:        "0 0 0 * * *"
--     billing-reconcile-cron:     "0 0 */6 * * *"
--     billing-order-expire-cron:  "0 */15 * * * *"
--     billing-renewal-retry-cron: "0 0 9 * * *"