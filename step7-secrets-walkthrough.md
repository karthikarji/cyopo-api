# Step 7 — Secrets & Configuration Wiring: What, Why, and How (Beginner-Friendly Deep Dive)

This document explains everything we did in Step 7, assuming **no prior Kubernetes
knowledge**. It covers the purpose, how it connects to earlier steps, every command and
what it does, the reasoning behind each decision, and analogies. Step 7 has no AWS
console work — it's all about getting your application's configuration into the cluster
correctly.

---

## Where Step 7 fits in the whole journey

By the end of Step 6 the dev environment had a working **data layer** (Postgres + Redis
pods). But a database sitting there is useless until an application connects to it — and
an application can't connect to anything until it's told *where* the database is, *what*
the password is, *which* API keys to use, and so on. That bundle of settings is the
application's **configuration**.

Step 7 prepares that configuration *inside the cluster*, so that when we deploy the app
in Step 8, it receives every setting it needs and starts cleanly.

**The direct connection to Step 1:** when you first ran the app in a container (Step 1),
it crashed repeatedly — once for the database URL, once for CORS, once for mail, once for
OpenAI — because each required setting was missing. You fixed it by passing a local
`.env.local` file. We called that file your **"config contract."** Step 7 is where that
exact contract gets recreated *in the cluster* — same keys, cluster-appropriate values —
so the app won't hit those same crashes when it deploys.

### Analogy

Think of your application as a new employee on their first day. Before they can do any
work, they need: the address of the records room (database URL), the key to it
(password), the company credit-card numbers (Razorpay/Cloudinary keys), the email login
(mail credentials), and so on. Step 7 is preparing that **welcome packet**. In Step 8 the
employee shows up and is handed the packet; with it, they can start working immediately.

---

## Background concepts

Two Kubernetes objects do the work in this step:

- **ConfigMap** — a store for **non-sensitive** configuration (database *address*, Redis
  *host*, mail *server*, allowed web origins). Plain text, safe to read, fine to keep in
  version control (git).
- **Secret** — a store for **sensitive** values (passwords, API keys, tokens). Kept
  separate and handled carefully; never committed to git.

Both do the same fundamental job: they hold `KEY=VALUE` pairs that get injected into your
application pod as **environment variables** when it starts. This is the cluster's
equivalent of the `--env-file` you used locally in Step 1. Your application code reads
environment variables exactly as before — only the *source* of those variables changes
(from a local file to these cluster objects).

**Why split into two objects?** Separation by sensitivity. Non-secret config can live in
git so it's versioned and reviewable; secrets must stay out of git. Splitting them lets
you commit the ConfigMap freely while keeping the Secret private.

---

## Understanding YOUR app's configuration (the key analysis)

Before writing anything, we read your two config files — `application.yml` (the base) and
`application-dev.yml` (the dev profile) — to see exactly what the app reads and from
where. This mattered enormously, because getting a variable name wrong causes the same
crashes you saw in Step 1. We sorted every setting into three buckets:

**Bucket A — `${VAR}` with NO default → MUST be supplied or the app crashes:**
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `MAIL_USER`, `MAIL_PASSWORD`, `JWT_SECRET`.
These are the landmines — no fallback value, so if missing the app fails to start.

**Bucket B — `${VAR:default}` → has a fallback, won't crash, but the default is a
test/placeholder value:** `FRONTEND_URL`, `RAZORPAY_KEY_ID/SECRET/WEBHOOK_SECRET`,
`CLOUDINARY_CLOUD_NAME`, `OPENAI_API_KEY` (empty default), `SENTRY_DSN` (empty default).

**Bucket C — HARDCODED in `application-dev.yml`, NOT read from the environment:**
the datasource URL/username/password, Redis host/port, CORS origins, and mail host/port —
all hardcoded to **`localhost`** values in the dev profile.

### The critical problem Bucket C revealed

Your dev profile hardcodes the database and Redis to **`localhost`**. But inside the
cluster, `localhost` means *the app pod itself* — there is no database there. If we
deployed as-is, the app would look for a database inside its own pod, find nothing, and
fail to connect. (This is the very same trap from Step 1, where `localhost` inside a
container didn't reach your Mac's database.)

We had two ways to fix this:

- **Option 1 (chosen):** override the hardcoded values using environment variables. In
  Spring Boot, an environment variable *wins* over a value in a yml file. So we set
  `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, etc. in the ConfigMap, and they
  override the dev profile's `localhost` defaults *only in the cluster*. Your local runs
  keep using `localhost`. **No code change.**
- **Option 2 (rejected):** edit `application-dev.yml` to use `${...}` placeholders — but
  that would break your local `dev` runs unless you also set those vars locally. More
  disruptive.

We chose **Option 1** because it requires zero code changes and keeps your local
development working exactly as before — consistent with the "same image, different
environment variables" principle we've used since Step 1.

### The variable-name lesson (subtle but important)

Your dev profile reads mail credentials as `${MAIL_USER}` and `${MAIL_PASSWORD}` — your
*own custom* names. It does NOT use Spring's built-in `SPRING_MAIL_USERNAME` /
`SPRING_MAIL_PASSWORD`. So in the Secret we used **`MAIL_USER` / `MAIL_PASSWORD`**, the
names your active profile actually references. Using the Spring-native names would leave
the `${MAIL_USER}` placeholder unresolved and crash the app.

**The rule:** the environment-variable name must exactly match what the *currently active
config profile* reads. (Back in Step 1 we used `SPRING_MAIL_*` as a generic fallback;
now that the `dev` profile is active, its specific `${MAIL_USER}` names are what count.)
Noted for prod: `application-prod.yml` might use the Spring-native names instead — to be
checked when we build prod config.

---

## Stage 7.1 — The ConfigMap (non-sensitive config)

### Purpose — in plain English

Holds all the non-secret settings the app needs, including the env-var *overrides* that
redirect the app away from `localhost` toward the in-cluster `postgres` and `redis`
services. Safe to commit to git.

### The manifest (`k8s/dev-config.yaml`)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cyopo-config
  namespace: cyopo-dev
data:
  SPRING_PROFILES_ACTIVE: "dev"                                  # activate the dev profile
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/cyopo_db"  # override localhost -> postgres service
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATA_REDIS_HOST: "redis"                               # override localhost -> redis service
  SPRING_DATA_REDIS_PORT: "6379"
  CORS_ALLOWED_ORIGINS: "http://localhost:5173"                 # placeholder until dev frontend exists
  FRONTEND_URL: "http://localhost:5173"
  CLOUDINARY_CLOUD_NAME: "dnxfschao"
```

Field meanings:

- `SPRING_PROFILES_ACTIVE: dev` — tells the app to load `application-dev.yml` in the cluster.
- `SPRING_DATASOURCE_URL` → points at the `postgres` Service (from Step 6), overriding the
  profile's hardcoded `localhost`. This is the line that makes the app find its database.
- `SPRING_DATA_REDIS_HOST: redis` → same override for the Redis Service.
- `CORS_ALLOWED_ORIGINS` → overrides the hardcoded CORS list; a placeholder for now,
  updated (one-line edit + pod restart) when a real dev frontend exists.

### Command

```bash
kubectl apply -f k8s/dev-config.yaml
```

*Purpose:* creates the ConfigMap in `cyopo-dev`. Safe to commit this file to git (no
secrets in it).

---

## Stage 7.2 — The Secret (sensitive values)

### Purpose — in plain English

Holds the passwords and API keys. The important discipline here: we create this **without
writing the secret values into a committed file and without pasting them into any chat**.
The values live only on your machine and inside the cluster.

### How we created it — from a local, gitignored file

**1. A local secrets file (`k8s/.dev-secrets.env`)** — plain `KEY=VALUE` lines, the same
format as your Step 1 `.env.local`. It contains the 11 sensitive values:
`SPRING_DATASOURCE_PASSWORD`, `MAIL_USER`, `MAIL_PASSWORD`, `JWT_SECRET`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`,
`RAZORPAY_WEBHOOK_SECRET`, `OPENAI_API_KEY`, `SENTRY_DSN`.

**2. Gitignore it immediately** so it can never be committed:

```bash
echo "k8s/.dev-secrets.env" >> .gitignore
```

*Purpose:* tells git to ignore the file. Secrets stay off version control.

**3. Create the Secret from that file:**

```bash
kubectl create secret generic cyopo-secrets \
  --namespace cyopo-dev \
  --from-env-file=k8s/.dev-secrets.env
```

*Purpose:* `create secret generic` makes a Secret named `cyopo-secrets`; `--from-env-file`
reads each line of the local file and stores it as a secret entry. The values go straight
from your machine into the cluster — never into git, never into chat.

### Verifying (without exposing the values)

```bash
kubectl describe secret cyopo-secrets -n cyopo-dev
```

*Purpose:* lists each key name and its **byte size** (not the actual value), so you can
confirm all 11 keys loaded and none is empty — safe to look at and share.

### The mistake we caught (and why the byte counts matter)

On the first attempt, the Secret was created from the *template* before the real values
were filled in — so several keys held placeholder text like `your_jwt_secret`. We spotted
this from the **byte counts**: e.g. `JWT_SECRET` showed 15 bytes (the length of
`your_jwt_secret`), but a real JWT secret is ~88 bytes. The fix was to fill in the real
values and recreate the Secret:

```bash
kubectl delete secret cyopo-secrets -n cyopo-dev
kubectl create secret generic cyopo-secrets --namespace cyopo-dev --from-env-file=k8s/.dev-secrets.env
```

*(A Secret can't be edited from a file in place, so delete-and-recreate is the clean way.
Safe here because nothing was using it yet.)* After the fix, `JWT_SECRET` grew to its real
length — the signal that real values went in.

### A key consistency check

`SPRING_DATASOURCE_PASSWORD` in this Secret **must equal** the password in the dev
Postgres pod's own Secret (both `cyopodev2904`). If they differ, the app authenticates
with the wrong password and gets a confusing "password authentication failed" at deploy
time. We confirmed they match.

---

## How this gets used in Step 8

When we deploy the app, its Deployment will reference **both** objects:

```yaml
      envFrom:
        - configMapRef:
            name: cyopo-config
        - secretRef:
            name: cyopo-secrets
```

`envFrom` means "inject every key from these as environment variables." Kubernetes then
hands the pod all ~18 variables at startup — the full cloud equivalent of `--env-file`.
Same image, dev values, zero code change. This is the moment the Step 7 "welcome packet"
gets handed to the employee.

---

## Summary: what Step 7 achieved

| Object                     | Holds                                   | Committed to git?                       |
|----------------------------|-----------------------------------------|-----------------------------------------|
| `cyopo-config` (ConfigMap) | Non-secret config + localhost overrides | Yes (`k8s/dev-config.yaml`)             |
| `cyopo-secrets` (Secret)   | 11 passwords/keys                       | No (from gitignored `.dev-secrets.env`) |

**End state:** the dev environment's configuration is fully staged in the cluster. The
app, when deployed, will receive correct database/Redis addresses (overriding the
profile's localhost), the right credentials, and all API keys — without code changes and
without secrets touching git.

**The big lessons worth keeping:**

1. Cluster config = ConfigMap (non-secret) + Secret (sensitive), injected as env vars —
   the cloud version of `.env`.
2. Environment variables override yml values in Spring Boot — use this to redirect
   hardcoded `localhost` to in-cluster service names without changing code.
3. Variable names must match what the *active profile* actually reads (`MAIL_USER`, not
   `SPRING_MAIL_USERNAME`, in our dev profile).
4. Never commit secrets: create the Secret from a gitignored local file; verify with
   `describe` (byte counts), never by printing values.
5. The DB password in the app's Secret must match the database's own password.