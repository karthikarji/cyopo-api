-- ─── Update FREE plan ─────────────────────────────────────────────
UPDATE billing.plans
SET
    max_portfolios = 2,
    features = '[
        "2 portfolios",
        "Up to 3 projects per portfolio",
        "Free templates only",
        "Resume upload",
        "Full analytics",
        "Contact messages",
        "cyopo branding shown"
    ]'::jsonb
WHERE name = 'FREE';

-- ─── Update PREMIUM plan ──────────────────────────────────────────
UPDATE billing.plans
SET
    max_portfolios = 5,
    features = '[
        "Up to 5 portfolios",
        "Unlimited projects",
        "All templates including premium",
        "Custom domain",
        "Resume upload",
        "Full analytics",
        "Contact messages",
        "No cyopo branding"
    ]'::jsonb
WHERE name = 'PREMIUM';

-- ─── Update PRO plan ──────────────────────────────────────────────
UPDATE billing.plans
SET features = '[
    "Unlimited portfolios",
    "Unlimited projects",
    "All templates including premium",
    "Custom domain",
    "Resume upload",
    "Full analytics",
    "Priority support",
    "Remove cyopo branding",
    "Early access to new features"
]'::jsonb
WHERE name = 'PRO';