## STEP 11 — FRONTEND (S3 + CloudFront)

- **Architecture:** Vite build (static) → S3 (private bucket) → CloudFront (CDN, HTTPS via OAC).
  Frontend env vars are PUBLIC (baked in at build time); only `VITE_`-prefixed vars exposed.
- **Frontend reads only:** `VITE_API_URL` + `VITE_RAZORPAY_KEY_ID` (+ built-in `import.meta.env.DEV`).
  No trailing slash on `VITE_API_URL` (code appends `/api/v1/...`).
- **Env files:** `.env.development` (VITE_API_URL=https://api-dev.cyopo.com),
  `.env.production` (VITE_API_URL=https://api.cyopo.com). Safe to commit (public values).
  Build dev: `npm run build -- --mode development`; build prod: `npm run build`.
- **Dev frontend:** bucket `cyopo-frontend-dev`, CloudFront dist `ED8WZAJ70YBI2`
  (`d31epgq38edcjv.cloudfront.net`), domain `app-dev.cyopo.com`, **Basic Auth gate** via CloudFront
  Function `cyopo-dev-basic-auth` (Viewer request event; base64 user:pass; whole site gated incl home).
- **Prod frontend:** bucket `cyopo-frontend-prod`, own CloudFront dist, `cyopo.com` + `www.cyopo.com`,
  NO gate (public product).
- **Required CloudFront settings (BOTH dists):** Origin = S3 REST endpoint + "Allow private S3 access"
  (OAC auto-configures bucket policy); Default root object = `index.html`; **SPA routing**: custom
  error responses 403→/index.html→200 AND 404→/index.html→200, error-caching-min-TTL = 0.
- **Route 53:** `app-dev` / `cyopo.com` / `www` → Alias A → respective CloudFront dist.
- **Backend CORS updates (CRITICAL — frontend won't work without):**
    - dev ConfigMap: `CORS_ALLOWED_ORIGINS=https://app-dev.cyopo.com`, `FRONTEND_URL=https://app-dev.cyopo.com`
    - prod ConfigMap: `ALLOWED_ORIGINS=https://cyopo.com,https://www.cyopo.com` (prod uses ALLOWED_ORIGINS, no trailing
      slash)
- **Gotchas hit:** prod frontend was accidentally built/uploaded pointing at DEV api (CORS error from
  cyopo.com→api-dev) — fixed by rebuilding with `.env.production` + re-sync + CloudFront invalidation;
  deep-link AccessDenied XML = missing SPA error responses (fixed); stray `/vite.svg` 403 (cosmetic).
- **Deploy refresh pattern:** `aws s3 sync dist/ s3://BUCKET/ --delete` then
  `aws cloudfront create-invalidation --distribution-id DIST --paths "/*"`.

## THE v3 INCIDENT — health probe / mail restart loop (major debugging chain)

- **Symptom:** dev pod restarted 144 times (~every 17 min); intermittent 503s to users at login.
- **Root cause chain:** liveness probe hit full `/actuator/health` → returned 503 → pod killed/restarted.
  Health was DOWN because the **mail health indicator** authenticated to Gmail SMTP on every poll
  (every 10-15s), so Gmail rate-limited it: "454-4.7.0 Too many login attempts." Self-inflicted loop.
  (db/redis/disk were all UP — only mail.) Single replica meant every restart = brief outage.
- **Fix (image v3, two code changes):**
    1. `SecurityConfig`: `/actuator/health` → `/actuator/health/**` (sub-paths /liveness, /readiness
       were returning 401, blocking the new probes).
    2. `application.yml`: `management.health.mail.enabled: false` (+ `endpoint.health.probes.enabled: true`).
    3. Manifests: startupProbe + readinessProbe → `/actuator/health/readiness`,
       livenessProbe → `/actuator/health/liveness` (group endpoints exclude external deps).
- **Lessons:** liveness must NOT depend on external deps; permitting `/actuator/health` doesn't cover
  `/**`; don't put aggressive external checks (SMTP) in health indicators; single replica = restart is outage.
- **YAML gotcha during fix:** accidentally changed `apiVersion: apps/v1` → `apps/v3` (wrong — apps/v1 is
  the K8s API version, unrelated to image tag) and left image at v2. The IMAGE line is what goes to v3.
- **Result:** dev + prod on v3, `1/1 Running`, 0 restarts, health fully UP (no mail component).
- **OUTSTANDING:** prod came back up at replicas:2 after the v3 apply — scale back to 1
  (`kubectl scale deployment cyopo-api -n cyopo-prod --replicas=1`) + fix prod-app.yaml to replicas:1.

## EARLIER STEPS (1-10) — condensed

- **Steps 1-5:** Docker multi-stage build; AWS CLI/IAM; ECR; EKS cluster by hand (VPC wizard, subnet
  tags, cluster+node IAM roles, Graviton node group in PRIVATE subnets); Helm NGINX Ingress + cert-manager.
- **Step 6:** dev Postgres 17 + Redis pods (debugging chain: default StorageClass, EBS CSI driver addon,
  Pod-Identity IAM for driver, mount volume one level up at /var/lib/postgresql w/ PGDATA subdir).
  Prod RDS created (Single-AZ db.t4g.micro gp3 20GB, Public=No, cyopo-rds-sg, cyopo-db-subnet-group,
  initial db cyopo_db, backups 7d, Insights Standard, ~$17-28/mo).
- **Step 7:** dev ConfigMap `cyopo-config` + Secret `cyopo-secrets` (11 keys, from gitignored
  .dev-secrets.env). Dev profile reads MAIL_USER/MAIL_PASSWORD (not SPRING_MAIL_*); env vars override yml.
- **Step 8:** dev app deployed; Flyway built 4 schemas; probe-timeout fix (timeoutSeconds:5); springdoc
  2.6.0→2.7.0 fix (NoSuchMethodError w/ Spring 6.2) → first hand rebuild to v2.
- **Step 9:** Route 53 (Hostinger NS change — strip trailing dots, remove parking NS); ClusterIssuer
  letsencrypt-prod (HTTP-01); dev Ingress (api-dev.cyopo.com, proxy-body-size 15m); cert issued.
- **Step 10:** prod profile uses DIFFERENT var names (DATABASE_URL/DB_USERNAME/DB_PASSWORD/REDIS_HOST/
  ALLOWED_ORIGINS — NOT SPRING_*); prod Redis pod; prod ConfigMap+Secret; prod app replicas:1;
  api.cyopo.com Ingress + cert (reused cluster NLB + ClusterIssuer; routed by hostname).

## ARCHITECTURE DECISIONS

- EKS (1 cluster, 2 namespaces cyopo-dev/cyopo-prod) — user chose K8s for learning + scale-readiness,
  knows EC2/Fargate simpler; plans to evaluate EC2 alternative AFTER finishing.
- Cost ~$170-210/mo (control plane + 2 nodes + NLB + RDS + small EBS + CloudFront).
- Image-promotion model: same image (v3) across dev/prod, differ by namespace/config/database.
- Monitoring: Sentry only (Grafana/CloudWatch dropped for now).