# cyopo — Deployment Architecture & Decisions

**Project:** cyopo ("Create Your Own Portfolio") — full-stack SaaS
**Document purpose:** Single source of truth for every deployment decision made, the reasoning behind it, and where it
sits in the build sequence.
**Last updated:** 2026-06-09

---

## 1. Quick-Reference Decision Table

| Area                  | Decision                                                | Why (one line)                                                        |
|-----------------------|---------------------------------------------------------|-----------------------------------------------------------------------|
| Cloud provider        | AWS, region `ap-south-1` (Mumbai)                       | Closest to user (Hyderabad) and Razorpay; low payment/webhook latency |
| Orchestration         | Kubernetes via **EKS**                                  | Requested; portable, industry-standard                                |
| Cluster topology      | **1 cluster, 2 namespaces** (`cyopo-dev`, `cyopo-prod`) | Saves ~$73/mo vs 2 clusters; fits image-promotion model               |
| Environments          | `dev` and `prod`                                        | Standard separation; same image promoted across both                  |
| Ingress               | **NGINX Ingress Controller** behind one NLB             | Requested; portable, host-based routing, free Let's Encrypt TLS       |
| TLS / certs           | **cert-manager + Let's Encrypt**                        | Free, auto-renewing; terminates at NGINX                              |
| Prod database         | **RDS PostgreSQL** (Single-AZ → Multi-AZ later)         | Durable, backups, PITR; safe home for billing/GST invoice data        |
| Dev database          | **In-cluster Postgres pod**                             | Perfect version parity with prod RDS; ~$0; disposable                 |
| Cache / rate limiting | **In-cluster Redis pod** (both envs)                    | Rate-limit data is loss-tolerant; avoids ElastiCache cost             |
| Object storage        | **Cloudinary** (unchanged)                              | Already integrated; free tier sufficient                              |
| Email                 | **Amazon SES** (replacing Gmail SMTP)                   | Gmail throttles/blocks transactional volume; SES cheap & reliable     |
| Payments              | **Razorpay** (test → live)                              | Already integrated; webhook needs raw-body integrity                  |
| Container registry    | **ECR**                                                 | Native AWS; lifecycle policy to expire old images                     |
| Frontend hosting      | **S3 + CloudFront**                                     | Static Vite build; global CDN; cheap                                  |
| DNS                   | **Route 53** (domain bought on Hostinger)               | Smoother ACM/ALB/NLB integration; point Hostinger NS → Route 53       |
| Monitoring            | **Sentry** only (Grafana deferred)                      | Sentry already integrated; add Prometheus/Grafana later if needed     |
| CI/CD                 | **GitHub Actions + OIDC**, image promotion              | Build once, validate in dev, promote same SHA to prod                 |
| Scheduler scaling     | **ShedLock** (when scaling past 1 instance)             | Prevents billing cron jobs double-firing across replicas              |
| Build tool            | Multi-module Maven, Java 21, Spring Boot 3.4.5          | Existing project structure                                            |
| Container build       | Multi-stage Dockerfile, `-pl api -am`                   | Builds api + all dependency modules into one fat JAR                  |
| Config strategy       | `application.yml` defaults + env-var overrides          | Same image runs local/dev/prod by swapping env vars only              |

---

## 2. Detailed Decisions

### 2.1 Cloud provider & region

- **Provider:** AWS.
- **Region:** `ap-south-1` (Mumbai).
- **Reasoning:** Lowest latency to the user's location (Hyderabad) and to Razorpay's
  infrastructure, which matters for payment flows and webhook round-trips.

### 2.2 Cluster topology — one cluster, two namespaces

- **Decision:** A single EKS cluster hosting two namespaces, `cyopo-dev` and `cyopo-prod`.
- **Reasoning:** Each EKS cluster carries a ~$73/month control-plane fee regardless of load.
  Two clusters = ~$146/month fixed before any compute. One cluster halves that and fits the
  "promote the same image dev→prod" model cleanly (promotion = deploy into the second namespace).
- **Tradeoff accepted:** Namespace isolation is weaker than cluster isolation. Mitigated with
  ResourceQuotas, NetworkPolicies, and separate IAM roles per namespace.
- **Cost watch:** EKS control-plane fee jumps from $0.10/hr to $0.60/hr once the Kubernetes
  version exits standard support (~14 months). **Upgrade before then.**

### 2.3 Ingress — NGINX Ingress Controller

- **Decision:** NGINX Ingress Controller running as in-cluster pods, fronted by a single AWS NLB.
- **Routing:** Host-based — `api.<domain>` → prod namespace, `api-dev.<domain>` → dev namespace.
- **Reasoning:** Portable (standard K8s, not AWS-locked), flexible, and pairs with free
  Let's Encrypt certs. One NLB serves both environments.
- **NGINX responsibilities for cyopo:**
    - Core: routing + TLS termination.
    - Turn on: request body-size limit (raise above 1 MB default for Cloudinary uploads —
      avoids cryptic 413 errors), security headers (HSTS, X-Frame-Options, X-Content-Type-Options),
      gzip compression.
    - Optional: edge rate limiting (coarse flood protection — complements, does not replace,
      the granular Redis-based limiter in the auth module).
    - Later: canary deployments (weighted traffic split for gradual prod rollouts).
    - **Do NOT:** alter the Razorpay webhook raw body (breaks HMAC signature verification);
      handle CORS (kept in Spring); serve the React frontend (CloudFront does that).

### 2.4 Databases — split by environment

- **Prod:** RDS PostgreSQL. Single-AZ `db.t4g.micro` to start; move to Multi-AZ when downtime
  has real cost. Automated backups + point-in-time recovery on. Lives in a private subnet.
    - **Reasoning:** Stores billing, payments, and GST invoice data with legal retention needs.
      Needs durability and an always-warm connection (HikariCP doesn't tolerate scale-to-zero well).
- **Dev:** In-cluster Postgres pod in `cyopo-dev` with a small persistent volume.
    - **Reasoning:** Exact version parity with prod RDS (critical for testing Flyway migrations
      that will later run against the revenue database); ~$0; fully disposable.
- **Neon (considered, not chosen):** Free tier (0.5 GB/project, scale-to-zero) is great for
  dev but rejected because: cold starts hurt HikariCP, 0.5 GB cap + usage-based model is wrong
  for billing data, and it adds an external dependency. Its branching feature is the only thing
  that would flip dev back to Neon — only if a throwaway-copy-heavy workflow emerges.

### 2.5 Cache — in-cluster Redis

- **Decision:** Redis pod inside each namespace, used only for rate limiting.
- **Reasoning:** Rate-limit counters are loss-tolerant (fine to reset on restart), so paying
  for ElastiCache (~$12–15/mo/node) is unnecessary. Upstash free tier was the alternative.

### 2.6 Storage, email, payments

- **Cloudinary:** Kept as-is; `StorageService` already built against it; free tier covers launch.
- **Email — Amazon SES:** Replaces Gmail SMTP, which throttles and eventually blocks
  transactional volume. `JavaMailSender` + Thymeleaf code unchanged — just a different SMTP host.
  New SES accounts start in sandbox (verified recipients only); file production-access request early.
- **Razorpay:** Webhook endpoint must be HTTPS (satisfied by NGINX TLS) and the raw request
  body must reach the app unmodified for HMAC signature verification.

### 2.7 Frontend & DNS

- **Frontend:** React + Vite + TypeScript build (`dist/`) on S3 + CloudFront with ACM cert.
- **DNS:** Domain purchased on Hostinger. Plan: point Hostinger nameservers at a Route 53
  hosted zone (~$0.50/mo) for smoother cert validation and NLB/CloudFront alias records.

### 2.8 Monitoring

- **Now:** Sentry only (already integrated; `SPRING_PROFILES_ACTIVE` tags environment).
- **Deferred:** Prometheus + Grafana. If added later, go lean self-hosted with short retention,
  one shared `monitoring` namespace scraping both dev and prod. Spring Actuator is already
  present; only the `micrometer-registry-prometheus` dependency would be needed.

### 2.9 CI/CD — GitHub Actions, image promotion

- **Auth:** GitHub Actions OIDC (no long-lived AWS keys in GitHub).
- **Flow:** On merge to `main` → build image once, tag with immutable git SHA → push to ECR →
  deploy SHA to dev namespace → smoke test → manual approval (GitHub environment protection rule)
  → deploy the *identical* SHA to prod namespace.
- **Principle:** The image is never rebuilt for prod. The same digest moves dev→prod, which
  eliminates "works in dev, breaks in prod" from rebuilds.

### 2.10 Scheduler scaling — ShedLock (future)

- **Issue:** The billing module's cron jobs (order expiry, subscription expiry, reconciliation,
  renewal retry) fire on every instance. With 2+ replicas this risks double-charging on renewal retry.
- **Plan:** Run a single backend replica at launch. Add ShedLock (Postgres-backed distributed lock)
  before scaling the web tier horizontally so each job fires exactly once cluster-wide.
- **Note:** Flyway is already safe under concurrent startup (it takes its own lock).

### 2.13 Network foundation — VPC (Step 4.1, console build)

- **VPC:** `cyopo-vpc` = `vpc-0bd1094e057481cd7`, CIDR `10.0.0.0/16`, region `ap-south-1`.
- **Built via** VPC console "VPC and more" wizard: 2 AZs (1a/1b), 2 public + 2 private subnets,
  1 internet gateway, **1 NAT gateway** (cost-optimized; ~$32/mo; the main billable network piece),
  route tables wired (public → IGW, private → NAT). DNS hostnames + resolution enabled.
- **Subnets:**
    - public1 `subnet-061f8aa133f0f06ce` (1a, 10.0.0.0/20)
    - public2 `subnet-0dc1e0f159028ebac` (1b, 10.0.16.0/20)
    - private1 `subnet-06be81e9524efccf4` (1a, 10.0.128.0/20)
    - private2 `subnet-0d19df936c3508921` (1b, 10.0.144.0/20)
- **EKS subnet tags (added manually — wizard does NOT add these):**
    - public subnets → `kubernetes.io/role/elb = 1` (internet-facing LBs land here; NGINX NLB)
    - private subnets → `kubernetes.io/role/internal-elb = 1` (internal LBs)
    - **Critical:** missing these = load balancer provisioning silently fails later. Verified present.
- Note: subnet `MapPublicIpOnLaunch` shows False even on public subnets — harmless; public-ness
  comes from IGW routing, not auto-assign-IP.

### 2.13b EKS cluster + node group (Step 4, console build)

- **Cluster:** `cyopo-cluster`, Kubernetes **1.35**, Custom configuration (Auto Mode OFF),
  Standard support (free), no scaling tier. Endpoint access: Public and private.
  Auth mode: EKS API. Envelope encryption: AWS-owned key (default). Deletion protection: off.
- **Cluster IAM role:** `cyopo-eks-cluster-role` (trusted entity EKS, policy `AmazonEKSClusterPolicy`).
- **Node IAM role:** `cyopo-eks-node-role` (trusted entity EC2; policies `AmazonEKSWorkerNodePolicy`,
  `AmazonEC2ContainerRegistryReadOnly`, `AmazonEKS_CNI_Policy`).
- **Add-ons installed:** kube-proxy, VPC CNI, CoreDNS, Pod Identity Agent (core), plus Metrics Server
  and Node monitoring agent. (External DNS deferred to Step 9 — installs before prerequisites exist.)
- **Node group:** `cyopo-nodes`, AMI `AL2023_ARM_64_STANDARD` (Graviton/arm64, matches image),
  `t4g.medium`, On-Demand, scaling desired 2 / min 2 / max 3. Placed in the **two PRIVATE subnets only**.
- **Lesson learned (the classic EKS-by-hand mistake):** first node-group attempt failed with
  `Ec2SubnetInvalidConfiguration` because a **public** subnet was selected for nodes. Nodes belong in
  **private** subnets (reach internet via NAT); load balancers go in public subnets. Failed node groups
  can't be edited — must delete and recreate. Fixed by recreating with private subnets only.
- **kubectl connected via:** `aws eks update-kubeconfig --region ap-south-1 --name cyopo-cluster`.

### 2.14 Container registry — ECR (decisions, Step 3)

- **Repository:** `cyopo-api`, **Private** (holds proprietary app code).
- **Tag mutability: Immutable** — tags can never be overwritten; new image = new tag.
  Underpins the "build once, promote same image dev→prod" guarantee. Cannot trust a
  promoted tag if its bytes could silently change.
- **Immutable tag exclusions: none** — every tag immutable, no carve-outs.
- **Encryption: AES-256** (AWS-managed, free) over KMS (own key, extra cost/complexity not
  needed for images). Note: encryption choice is permanent once the repo is created.
- **Image scanning:** per-repo toggle is deprecated; if wanted, enable once at registry level
  (ECR → Private registry → Settings).
- **Auth to push:** `aws ecr get-login-password | docker login --username AWS --password-stdin`.
  `AWS` is a fixed literal (not the IAM username); the token is short-lived and derived from the
  `aws configure` access key. No password is typed.
- **CPU architecture note:** local image built on Apple Silicon = arm64. Worker nodes in Step 4
  must match (→ use Graviton/arm64 nodes, which we wanted for cost anyway) or use multi-arch CI builds.

### 2.12 Config strategy (learned during Step 1)

- `application.yml` holds sane **local defaults**; every deployment **overrides via env vars**.
- Spring maps env vars to properties automatically (e.g. `SPRING_DATASOURCE_URL` →
  `spring.datasource.url`, `CORS_ALLOWED_ORIGINS` → `cors.allowed-origins`).
- Required `${...}` placeholders with no default will crash startup if the env var is missing.
  The full required-variable set is captured in `.env.local` — this is the config contract that
  must be mirrored (with different values) into Kubernetes secrets for dev and prod.
- **Hardening TODO:** give placeholders sensible defaults (e.g.
  `@Value("${cors.allowed-origins:http://localhost:3000}")`) so a missing var degrades gracefully.

---

## 3. Estimated Monthly Cost (launch, single cluster)

| Item                  | Optimized choice             | Est. /month (USD) |
|-----------------------|------------------------------|-------------------|
| EKS control plane     | 1 cluster, both namespaces   | ~$73              |
| Worker nodes          | Graviton (t4g), Spot for dev | ~$30–60           |
| Prod database         | RDS t4g.micro, Single-AZ     | ~$15–25           |
| Dev database          | In-cluster pod               | ~$0               |
| Redis                 | In-cluster pod               | ~$0               |
| Load balancer         | One NLB (shared)             | ~$18              |
| ECR + S3 + CloudFront | Lifecycle-managed            | ~$2–5             |
| SES + Cloudinary      | Free tiers                   | ~$0               |
| Route 53              | Hosted zone                  | ~$0.50            |
| **Total**             |                              | **~$140–180/mo**  |

Control-plane fee is the irreducible floor — the price of Kubernetes for two environments.
(ECS Fargate would remove ~$73/mo but was set aside in favour of the requested Kubernetes.)

---

## 4. Tech Stack (existing)

**Backend:** Java 21, Spring Boot 3.4.5, multi-module Maven (parent `cyopo`; modules:
`common`, `auth`, `template`, `core`, `billing`, `admin`, `api`). `api` is the runnable
entry point and owns `application.yml`; builds to a single fat JAR.
**Frontend:** React + Vite + TypeScript, Redux Toolkit, Tailwind CSS.
**Database:** PostgreSQL, 4 schemas (`auth`, `template`, `portfolio`, `billing`), Flyway migrations.
**Other:** Redis (rate limiting), Cloudinary (storage), Razorpay (payments),
JavaMail + Thymeleaf (email), Sentry (errors), Springdoc OpenAPI (Swagger).

---

## 5. Build Sequence & Progress

## ⚠️ SECURITY / PRE-LAUNCH TODO (do before going live)

- **Rotate exposed secrets** (pasted in chat during setup): JWT_SECRET (`Sr2Uz...==`),
  Gmail app password (`llae vnzt jqot pxnb`), Cloudinary API secret (`ec2Rww...`),
  Razorpay key secret, Sentry DSN. After rotating: update `.dev-secrets.env` + `.prod-secrets.env`,
  recreate the Secrets, rollout-restart.
- **Razorpay:** prod currently uses TEST keys (`rzp_test_...`). Swap to LIVE (`rzp_live_...`) +
  update webhook secret before taking real payments. Frontend gets live key ID; backend gets live secret.
- **Scheduler locking:** prod must stay `replicas: 1` until ShedLock (or leader election) is added —
  billing crons (order-expire, reconcile, renewal-retry, IP-masking) fire on EVERY replica.
- **Production email:** move off Gmail SMTP to Amazon SES (Step 12) — Gmail rate-limits hard
  (caused the "454 too many login attempts" incident). 1-2 day SES approval; start early.
- **Decide prod Swagger exposure:** currently public; consider locking `/swagger-ui` + `/v3/api-docs`
  behind auth or disabling in prod.
- **EKS version upgrade reminder** (~14 months) to avoid 6x control-plane fee.
- **CloudWatch logging** (optional): persistent searchable logs via Fluent Bit — not set up;
  Sentry + `kubectl logs` sufficient for now.

---

## KEY IDENTIFIERS

- AWS account **423623871239**, region **ap-south-1 (Mumbai)**, IAM user **cyopo-admin**.
- Domain **cyopo.com** (registered Hostinger, DNS on **Route 53**).
- Route 53 nameservers: ns-405.awsdns-50.com, ns-1511.awsdns-60.org, ns-652.awsdns-17.net, ns-2015.awsdns-59.co.uk
- VPC `vpc-0bd1094e057481cd7` (10.0.0.0/16). Public subnets (elb), private subnets (internal-elb).
- EKS cluster `cyopo-cluster` (K8s 1.35). Node group `cyopo-nodes`: 2x t4g.medium (Graviton/arm64), private subnets.
- ECR `cyopo-api`, immutable tags. **Current image: v3.** Full:
  `423623871239.dkr.ecr.ap-south-1.amazonaws.com/cyopo-api:v3`
- NGINX NLB: `a4ecc7be5be1b436aa2684332b20b940-f521fb6081558525.elb.ap-south-1.amazonaws.com`
- **LIVE URLS:**
    - Backend dev: `https://api-dev.cyopo.com` | prod: `https://api.cyopo.com`
    - Frontend dev: `https://app-dev.cyopo.com` (Basic Auth gated) | prod: `https://cyopo.com` (+ www)
- RDS prod: `cyopo-prod-db.c3qokeuskm9e.ap-south-1.rds.amazonaws.com:5432` (PostgreSQL 17, private)
- Wildcard ACM cert (us-east-1): `*.cyopo.com` + `cyopo.com` — covers all frontends.

---

## PROGRESS TRACKER

| Step | Description                                    | Status    |
|------|------------------------------------------------|-----------|
| 1    | Containerize backend (Docker)                  | ✅ Done    |
| 2    | AWS CLI / kubectl / IAM setup                  | ✅ Done    |
| 3    | ECR repo + push image                          | ✅ Done    |
| 4    | EKS cluster (VPC, nodes, IAM) by hand          | ✅ Done    |
| 5    | NGINX Ingress + cert-manager (Helm)            | ✅ Done    |
| 6    | Databases — dev pods done; prod RDS created    | ✅ Done    |
| 7    | Secrets/config wiring (dev)                    | ✅ Done    |
| 8    | Deploy to dev (manifests, Flyway, Swagger fix) | ✅ Done    |
| 9    | DNS + TLS for api-dev.cyopo.com                | ✅ Done    |
| 10   | Deploy to prod (RDS, api.cyopo.com)            | ✅ Done    |
| 11   | Frontend (S3+CloudFront, dev+prod)             | ✅ Done    |
| 12   | Production email (Amazon SES)                  | ⬜ Pending |
| 13   | CI/CD (GitHub Actions)                         | ⬜ Pending |

 
---

## 6. Outstanding Reminders

- **Rotate secrets** exposed during setup: Gmail app password, Cloudinary secret, JWT secret.
- **Never paste AWS access keys** into chat or commit them — feed directly into `aws configure`.
- **`.env.local`** is git-ignored and docker-ignored; it is the config contract for K8s secrets.
- **EKS version upgrade** reminder before standard support ends (~14 months) to avoid 6x fee.
- **Razorpay webhook** raw-body integrity must be preserved end-to-end.
- **IAM:** day-to-day work uses IAM user (in group `cyopo`, policy `AdministratorAccess`),
  not the root account. Tighten `AdministratorAccess` to least-privilege later.