# Step 10 — Deploying to Production: Full Walkthrough

This document explains everything we did in Step 10, assuming **no prior AWS or
Kubernetes knowledge**. It covers the RDS database creation (every console setting), how
prod differs from dev, every file and command with line-by-line meaning, the issues we
hit, and analogies throughout.

---

## Where Step 10 fits

By the end of Step 9, the **dev** environment was fully live and public. Step 10 builds
the **production** environment — the one real users and real payments will use. It mirrors
dev, but with production-grade choices, the biggest being a **managed database (AWS RDS)**
instead of an in-cluster Postgres pod.

### Why prod uses RDS instead of a pod (the core decision)

Dev's database is a disposable pod — fine for throwaway test data. Prod holds **billing,
payment, and GST-invoice records**, which must be durable and recoverable. **RDS** is
AWS's managed database service: AWS runs it, patches it, backs it up automatically, and
can restore it to any point in time. You trade a small monthly cost (~$17–28) for not
having to operate a database yourself and for real data safety.

### Analogy

If dev's database is a whiteboard in a practice room, prod's RDS is a fireproof,
audited filing cabinet in a bank vault that someone else maintains, backs up nightly, and
can roll back if something goes wrong.

---

## Stage 10.1 — Creating the RDS instance (console, every setting)

The networking prep (subnet group + security group) was already done in Step 6, so this
was mostly paint-by-numbers. Console path: RDS → Databases → **Create database** →
**Full configuration** (the modern name for "Standard create" — full control over every
field; we avoided "Express/Easy" which would pick wrong network defaults).

### Settings, with the reason for each

- **Engine:** PostgreSQL **17.x** — matches dev and local, so Flyway migrations behave
  identically everywhere.
- **Template:** Production — sensible production defaults.
- **Availability:** **Single-AZ (1 instance)** — cheaper. (Multi-AZ adds automatic failover
  but ~doubles cost; upgrade later when downtime has real cost.)
- **DB instance identifier:** `cyopo-prod-db` — the name in the AWS console.
- **Master username:** `cyopo_admin` — the database superuser.
- **Credentials:** Self managed → a **strong password** (stored in a password manager,
  never pasted in chat). The app gets it via a Kubernetes Secret.
- **Instance class:** **`db.t4g.micro`** — Graviton (ARM), 2 vCPU / 1 GB, the smallest
  sensible prod size. *Chosen over `db.t3.micro` (Intel): same specs but Graviton is
  ~10–20% cheaper and consistent with our Graviton worker nodes.* **Watch this field —
  the form defaulted to a large class once, showing $74/mo; corrected to ~$17.**
- **Storage:** gp3, **20 GB**, autoscaling enabled (max ~100 GB).
- **Connectivity:**
    - **VPC:** `cyopo-vpc`.
    - **DB subnet group:** `cyopo-db-subnet-group` (the private-subnet group from Step 6).
    - **Public access: No** ← critical; the database is never reachable from the internet.
    - **VPC security group:** existing → **`cyopo-rds-sg`** (the firewall from Step 6 that
      allows Postgres only from the cluster nodes); the default SG removed.
    - **Database port:** 5432.
- **Database authentication:** Password authentication.
- **Monitoring:** Database Insights → **Standard** (NOT Advanced — Advanced is a paid
  tier with 15-month retention you don't need). **Enhanced Monitoring OFF**, **DevOps Guru
  OFF** (both paid add-ons).
- **Additional configuration:**
    - **Initial database name: `cyopo_db`** ← easy to miss; if blank, no database is created
      and the app has nowhere to connect.
    - **Backups:** enabled, 7-day retention (a main reason to use RDS).
    - **Encryption:** enabled (default `aws/rds` key — free, encrypts data at rest).
    - Log exports: unchecked.

Then **Create database** → ~5–10 min to provision (Creating → Available). Final cost
landed around **$17/month base** (instance ~$15 + storage), confirming Single-AZ +
`db.t4g.micro` were correctly chosen.

### The endpoint

Once Available, RDS → `cyopo-prod-db` → Connectivity & security → **Endpoint**:

```
cyopo-prod-db.c3qokeuskm9e.ap-south-1.rds.amazonaws.com:5432
```

This is the host the app connects to. (It's safe to know — it's not publicly reachable,
since Public access = No.)

---

## The KEY difference: prod reads DIFFERENT env-var names than dev

Before any config, we read `application-prod.yml` and found it uses **different variable
names** than the dev profile. This is the single most important thing to get right —
using dev's names in prod would crash the app (unresolved `${...}` placeholders).

| What           | Dev profile read              | **Prod profile reads**                     |
|----------------|-------------------------------|--------------------------------------------|
| DB URL         | `SPRING_DATASOURCE_URL`       | **`DATABASE_URL`**                         |
| DB username    | `SPRING_DATASOURCE_USERNAME`  | **`DB_USERNAME`**                          |
| DB password    | `SPRING_DATASOURCE_PASSWORD`  | **`DB_PASSWORD`**                          |
| Redis host     | `SPRING_DATA_REDIS_HOST`      | **`REDIS_HOST`**                           |
| Redis port     | `SPRING_DATA_REDIS_PORT`      | **`REDIS_PORT`**                           |
| CORS           | `CORS_ALLOWED_ORIGINS`        | **`ALLOWED_ORIGINS`**                      |
| Mail user/pass | `MAIL_USER` / `MAIL_PASSWORD` | same (+ defaulted `MAIL_HOST`/`MAIL_PORT`) |

Unlike the dev profile (which hardcoded `localhost`), `application-prod.yml` uses `${...}`
placeholders for **everything** — it's designed to be fully configured by environment.
**The rule, again:** env-var names must match what the *active profile* reads.

---

## Stage 10.2 — Prod Redis (in-cluster pod)

Same as dev — Redis only holds throwaway rate-limit data, so an in-cluster pod is fine for
prod too (no managed service needed). File `k8s/prod-redis.yaml`: a Deployment running
`redis:7-alpine` + a headless Service named `redis`, both in namespace `cyopo-prod`.

```bash
kubectl apply -f k8s/prod-redis.yaml
kubectl get pods -n cyopo-prod          # redis 1/1 Running
```

---

## Stage 10.3 — Prod ConfigMap (`k8s/prod-config.yaml`)

Non-sensitive config, using **prod variable names**, with the datasource pointing at the
**RDS endpoint**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cyopo-config
  namespace: cyopo-prod
data:
  SPRING_PROFILES_ACTIVE: "prod"                    # activate application-prod.yml
  DATABASE_URL: "jdbc:postgresql://cyopo-prod-db.c3qokeuskm9e.ap-south-1.rds.amazonaws.com:5432/cyopo_db"
  DB_USERNAME: "cyopo_admin"                        # RDS master user (non-secret)
  REDIS_HOST: "redis"                               # in-cluster prod Redis service
  REDIS_PORT: "6379"
  MAIL_HOST: "smtp.gmail.com"
  MAIL_PORT: "587"
  MAIL_USER: "karthikarji22@gmail.com"              # just an email address (non-secret)
  ALLOWED_ORIGINS: "https://cyopo.com"              # future frontend domain (prod points at real domain)
  FRONTEND_URL: "https://cyopo.com"
  CLOUDINARY_CLOUD_NAME: "dnxfschao"
```

```bash
kubectl apply -f k8s/prod-config.yaml
```

---

## Stage 10.4 — Prod Secret (`k8s/.prod-secrets.env` → Secret)

Sensitive values, with **prod names**, created from a gitignored local file (never in git,
never in chat):

```
DB_PASSWORD=<RDS master password>      # NOT dev's pod password — the one set in the RDS console
MAIL_PASSWORD=<gmail app password>
JWT_SECRET=<jwt secret>
CLOUDINARY_API_KEY=<...>
CLOUDINARY_API_SECRET=<...>
RAZORPAY_KEY_ID=<test key for now>     # TEST keys in prod until taking real payments
RAZORPAY_KEY_SECRET=<test secret>
RAZORPAY_WEBHOOK_SECRET=<...>
SENTRY_DSN=<...>
OPENAI_API_KEY=<... or placeholder>
```

```bash
echo "k8s/.prod-secrets.env" >> .gitignore
kubectl create secret generic cyopo-secrets --namespace cyopo-prod --from-env-file=k8s/.prod-secrets.env
kubectl describe secret cyopo-secrets -n cyopo-prod
```

We verified the byte counts matched real values (e.g. `JWT_SECRET: 88 bytes` = the real
key; `DB_PASSWORD: 13 bytes` = the real RDS password). **The most important check:**
`DB_PASSWORD` must equal the RDS master password set in the console — a mismatch causes
`password authentication failed` at startup.

---

## Stage 10.5 — Deploy the app to prod (`k8s/prod-app.yaml`)

The **same `cyopo-api:v2` image** as dev (the image-promotion model: identical artifact,
prod config), in namespace `cyopo-prod`, with the **probe-timeout fix carried over from
Step 8**. Key field: **`replicas: 1`**.

### Why replicas: 1 (the scheduler consideration)

Your app has scheduled jobs (the billing crons in `application.yml`). With multiple
replicas, **every replica runs the schedulers**, so a cron could fire two (or more) times,
double-processing billing tasks. Until distributed locking (e.g. ShedLock) is added, prod
runs **1 replica** to guarantee each job runs once. (Recorded as a pre-scale TODO.)

```bash
kubectl apply -f k8s/prod-app.yaml
kubectl get pods -n cyopo-prod -w
```

### What happens on first boot

With `ddl-auto: validate` and Flyway enabled, the prod app's first boot **runs all Flyway
migrations against the empty RDS database**, building the `auth`, `template`, `portfolio`,
and `billing` schemas — exactly as it did in dev, but now in the managed database. This
first boot takes a bit longer because of the migrations.

### The hiccup we hit

The deploy initially came up with **2 replicas** (the file/apply timing left `replicas: 2`).
Since prod's billing data was empty, no harm done, but we corrected it:

```bash
kubectl scale deployment cyopo-api -n cyopo-prod --replicas=1   # fix running state now
# + edit k8s/prod-app.yaml to replicas: 1 so it persists on next apply
```

### Verifying prod connected to RDS

```bash
kubectl exec -it -n cyopo-prod deploy/cyopo-api -- env | grep DATABASE_URL
# -> confirmed DATABASE_URL points at the RDS endpoint

kubectl port-forward -n cyopo-prod svc/cyopo-api 8091:80
# then: curl http://localhost:8091/actuator/health  -> {"status":"UP"}
```

The health endpoint's readiness group checks the datasource connection. `UP` (and the pod
being `1/1 Running`) confirms the app successfully connected to RDS and Flyway built the
schemas. (Note: the app image is a slim JRE with no `psql`, so we verify via the health
check rather than a direct DB client — the health check is the better proof anyway.)

---

## Stage 10.6 — DNS + TLS for `api.cyopo.com`

Same pattern as dev's `api-dev`, reusing the **same NLB and the same `letsencrypt-prod`
ClusterIssuer** (it's cluster-wide). Prod and dev share one load balancer; NGINX routes by
hostname (`api-dev.cyopo.com` → dev, `api.cyopo.com` → prod).

**Route 53:** Create record → name `api`, type `A`, Alias ON → Alias to the NLB →
Evaluate target health **No** → Create.

**Prod Ingress** (`k8s/prod-ingress.yaml`): identical structure to dev's, but host
`api.cyopo.com`, namespace `cyopo-prod`, TLS secret `cyopo-api-tls` (separate from dev's),
same `cert-manager.io/cluster-issuer: letsencrypt-prod` and `proxy-body-size: "15m"`.

```bash
kubectl apply -f k8s/prod-ingress.yaml
kubectl get certificate -n cyopo-prod -w     # cyopo-api-tls -> READY=True
```

Verified: `https://api.cyopo.com/actuator/health` returns UP with a trusted padlock.

---

## What Step 10 achieved

| Item                | Result                                                                          |
|---------------------|---------------------------------------------------------------------------------|
| Prod database       | RDS PostgreSQL 17 (`cyopo-prod-db`), Single-AZ, private, backed up, ~$17-28/mo  |
| Prod Redis          | in-cluster pod in `cyopo-prod`                                                  |
| Prod config/secrets | ConfigMap + Secret using prod var names; `DB_PASSWORD`=RDS password             |
| Prod app            | `cyopo-api:v2`, `replicas: 1`, healthy against RDS; Flyway built schemas in RDS |
| Public access       | `https://api.cyopo.com` live with trusted HTTPS                                 |

**End state:** production is fully live and public, running the same image as dev but
backed by the durable managed RDS database. Both environments now run the identical
artifact, separated by namespace, config, and database — the image-promotion model working
as designed.

**Durable lessons:** managed RDS for prod's important data (durable, backed up); prefer
Graviton (`t4g`) over Intel (`t3`) for the database (cheaper, same capability); match
env-var names to the active profile (prod's `DATABASE_URL`/`DB_PASSWORD` differ from dev's
`SPRING_*`); run a single replica until schedulers have distributed locking; and prod can
reuse the cluster's existing NLB and ClusterIssuer, separated by hostname.

---

## Outstanding pre-launch items recorded during Step 10

- Rotate exposed secrets (JWT, Gmail password, Cloudinary secret).
- Swap Razorpay TEST keys → LIVE keys before real payments.
- Add scheduler locking (ShedLock) before scaling prod beyond 1 replica.
- Decide whether to lock prod Swagger/`v3/api-docs` behind auth or disable it.