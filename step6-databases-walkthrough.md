# Step 6 — Databases: What, Why, and How (Beginner-Friendly Deep Dive)

This document explains everything we did in Step 6, assuming **no prior Kubernetes
or AWS knowledge**. For each stage it covers the purpose, how it connects to earlier
steps, every command and what it does, the console settings and why we chose them,
and analogies to make the concepts stick. It also walks through the long debugging
chain we hit — which is genuinely the most instructive part.

---

## Where Step 6 fits in the whole journey

By the end of Step 5 we had:

- a **cluster** (Step 4) — the staffed campus with worker machines,
- a **front door** (Step 5) — NGINX Ingress + an NLB, so internet traffic *could* get in,
- but **no data layer**. An application needs somewhere to store data (user accounts,
  portfolios, billing records) and somewhere fast to track things like rate limits.

Step 6 builds that data layer. Continuing the campus analogy: if the cluster is the
campus and the app (coming in Step 8) is the staff doing work, then **the database is
the filing system / records room** where all the important paperwork is stored, and
**Redis is the sticky-note board** for fast, temporary jotting (rate-limit counters).

A core decision shaped this whole step: **dev and prod get different data solutions.**

- **Dev** (where we build and test): cheap, disposable databases running *inside* the
  cluster as pods. Throwaway data, no real cost.
- **Prod** (real users, real money): a proper managed database (AWS RDS) with backups
  and durability, because it holds billing/payment/GST records. (We ended up
  **deferring** prod RDS until just before launch to avoid paying for an idle database.)

Redis is only doing rate limiting in both — and rate-limit data is fine to lose on a
restart — so an in-cluster Redis pod is good enough for both environments.

---

## Background concepts (read this first if you're new to Kubernetes)

A few terms used throughout, in plain English:

- **Namespace** — a labelled partition *inside* the cluster. Like separate floors of a
  building. We use `cyopo-dev` and `cyopo-prod` so dev and prod resources don't mix.
- **Pod** — the smallest running unit in Kubernetes; basically "a running container."
  Our Postgres pod is a running Postgres container.
- **Deployment** — a manager that keeps a pod running. If the pod crashes, the
  Deployment starts a new one. You describe the desired state; it maintains it.
- **Service** — a *stable name and address* for a pod. Pods get random IP addresses that
  change when they restart, so you never talk to a pod directly — you talk to its
  Service, which always points to the current pod. Think of it as a permanent phone
  extension that always rings the right desk even when staff change.
- **Secret** — Kubernetes' store for sensitive values (passwords). Kept separate from
  regular config.
- **PersistentVolumeClaim (PVC)** — a *request* for durable disk storage that survives
  pod restarts. On AWS, this gets fulfilled by a real **EBS volume** (a cloud hard disk).
- **Manifest** — a YAML file describing what you want Kubernetes to create. You `apply`
  it and Kubernetes makes reality match the file.
- **`kubectl`** — the command-line tool for talking to the cluster (set up in Step 4).

---

## Stage 6.0 — Create the namespaces

### Purpose — in plain English

Before we can put a database "in dev," the dev partition has to exist. Namespaces are
those partitions. We create two: one for the dev environment, one for prod. Everything
for dev (database, cache, and later the app) goes in `cyopo-dev`; everything for prod
in `cyopo-prod`. This is the practical expression of the "one cluster, two namespaces"
decision we made at the very beginning.

### Commands

```bash
kubectl create namespace cyopo-dev
kubectl create namespace cyopo-prod
```

*Purpose:* each command carves out one partition. They start empty.

```bash
kubectl get namespaces
```

*Purpose:* lists all namespaces to confirm the two new ones exist alongside the
system ones (`kube-system`, `ingress-nginx`, `cert-manager`).

### Analogy

You're about to move furniture (databases) into two offices. First you have to
*designate* the two offices. That's what creating the namespaces does.

---

## Stage 6.1a — Dev Postgres (the main database)

### Purpose — in plain English

This gives the dev environment a real PostgreSQL database, running as a pod inside the
cluster, with a durable disk so its data survives restarts. Your application will
connect to it exactly as it connected to your laptop's Postgres during Step 1 — but now
the database lives in the cluster instead of on your Mac. The connection address changes
from `host.docker.internal` (local) to `postgres` (the Service name in the cluster);
the app code doesn't change, only an environment variable does.

### How it connects to earlier steps

In Step 1 you ran the app against a local Postgres. In Step 7 you'll point the app's
`SPRING_DATASOURCE_URL` at *this* database. We deliberately match the Postgres **version
(17)** to your local and future-prod databases so your Flyway migrations behave
identically everywhere.

### The four objects (and why a database needs all four)

A database in Kubernetes is built from four cooperating pieces:

1. **PersistentVolumeClaim (PVC)** — requests a durable 5 GB disk (an AWS EBS volume).
   Without this, the database's files would vanish every time the pod restarted.
2. **Secret** — holds the DB name, username, and password, kept out of the main file.
3. **Deployment** — runs the actual Postgres 17 container and keeps it alive.
4. **Service** — gives the pod the stable address `postgres` that the app will use.

### The manifest (saved as `k8s/dev-postgres.yaml`)

Key fields and their meaning:

- `image: postgres:17` — the official Postgres 17 container.
- `envFrom.secretRef: postgres-secret` — injects the DB credentials from the Secret so
  Postgres initializes with the right name/user/password.
- `volumeMounts` + `volumes.persistentVolumeClaim` — attaches the durable disk.
- `PGDATA: /var/lib/postgresql/data` with the volume mounted at `/var/lib/postgresql`
  (one level up) — **this exact arrangement is the fix for two bugs we hit; see the
  debugging section below.**
- Service `clusterIP: None` — a "headless" service, standard for databases; gives the
  hostname `postgres` reachable inside the namespace.

### Command

```bash
kubectl apply -f k8s/dev-postgres.yaml
```

*Purpose:* creates all four objects at once. `apply` means "make the cluster match this
file." You run it again after any edit and it updates only what changed.

### Analogy

The PVC is renting a storage locker (durable disk). The Secret is the combination to the
locker and the office. The Deployment is hiring the records clerk (Postgres) and keeping
them on duty. The Service is the permanent phone extension that always reaches the clerk.

---

## The debugging chain (the most instructive part of Step 6)

Getting that one Postgres pod running took fixing **four** separate problems in
sequence. This is realistic — stateful workloads on Kubernetes commonly hit these. Each
is explained below with what we saw, why it happened, and the command that fixed it.

### Problem 1 — The storage class wasn't the default

**Symptom:** the PVC sat in `Pending` forever; the pod couldn't start because it had no
disk.

**Why:** a "StorageClass" is a *rule* describing how to create disks. Our cluster had one
called `gp2`, but it wasn't marked as the *default*, so when our PVC asked for a disk
without naming a class, Kubernetes didn't know which rule to use — and just waited.

**Diagnosis:**

```bash
kubectl get storageclass
```

This showed `gp2` existed but had no `(default)` marker.

**Fix:**

```bash
kubectl patch storageclass gp2 -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
```

*Purpose:* marks `gp2` as the default so any PVC that doesn't name a class uses it.

**Analogy:** you asked the building for "a storage locker" but never said which locker
bank, and no bank was marked "use this one by default" — so your request just hung.

### Problem 2 — The EBS CSI driver wasn't installed

**Symptom:** even with the default set, the PVC stayed `Pending`.

**Why:** the StorageClass is only the *recipe* for making disks. Something actually has
to *do the work* of calling AWS to create an EBS volume — that worker is the **EBS CSI
driver**, an add-on that wasn't installed on the cluster. Recipe present, cook absent.

**Diagnosis:**

```bash
kubectl get pods -n kube-system | grep ebs
```

Returned nothing → the driver wasn't there.

**Fix:**

```bash
aws eks create-addon --region ap-south-1 --cluster-name cyopo-cluster --addon-name aws-ebs-csi-driver
```

*Purpose:* installs the EBS CSI driver add-on, which watches for PVCs and creates the
real EBS disks in AWS to fulfill them.

**Analogy:** the kitchen had the recipe pinned to the wall but nobody hired a cook.
This command hires the cook.

### Problem 3 — The driver lacked AWS permission (crash loop)

**Symptom:** the driver pods installed but the controller showed `5/6` ready with the
restart count climbing fast — a crash loop. The PVC *still* wouldn't bind.

**Why:** to create EBS volumes in your AWS account, the driver needs **permission** — an
IAM role granting it. (Same idea as the cluster and node roles in Step 4: anything that
acts in AWS needs a permission badge.) The driver had no badge, so every time it tried
to create a volume AWS refused, and it crashed and restarted in a loop.

**Fix — give the driver a badge via "Pod Identity"** (the modern way to let a specific
pod assume an IAM role). Four commands:

```bash
cat > ebs-csi-trust.json << 'EOF'
{ "Version": "2012-10-17",
  "Statement": [ { "Effect": "Allow",
      "Principal": { "Service": "pods.eks.amazonaws.com" },
      "Action": ["sts:AssumeRole", "sts:TagSession"] } ] }
EOF
```

*Purpose:* writes a "trust policy" file saying "the EKS Pod Identity system may assume
this role." (This is the *who-can-wear-the-badge* half of an IAM role.)

```bash
aws iam create-role --role-name cyopo-ebs-csi-driver-role \
  --assume-role-policy-document file://ebs-csi-trust.json
```

*Purpose:* creates the IAM role (the badge) using that trust policy.

```bash
aws iam attach-role-policy --role-name cyopo-ebs-csi-driver-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy
```

*Purpose:* attaches the AWS-managed permission that actually allows creating/attaching
EBS volumes (the *what-the-badge-allows* half).

```bash
aws eks create-pod-identity-association --region ap-south-1 \
  --cluster-name cyopo-cluster --namespace kube-system \
  --service-account ebs-csi-controller-sa \
  --role-arn arn:aws:iam::423623871239:role/cyopo-ebs-csi-driver-role
```

*Purpose:* links the driver's identity (its "service account") to the new role, so the
driver pods now run *with* those AWS permissions.

```bash
kubectl rollout restart deployment ebs-csi-controller -n kube-system
```

*Purpose:* restarts the driver pods so they pick up the new permissions. After this the
crash loop stopped (pods went `6/6`, 0 restarts) and the PVC bound within a minute.

**Analogy:** the cook showed up but had no keycard to the supply room, so they kept
getting turned away at the door (crash loop). We issued a keycard (IAM role), programmed
it for the supply room (the policy), gave it to that specific cook (pod identity
association), and sent them back in (restart). Now they can fetch supplies.

### Problem 4 — Postgres init + lock-file issues on the EBS disk

This was actually two related bugs, fixed together by one arrangement.

**4a — "directory not empty" on first start.** When you attach a fresh EBS volume, it
comes with a hidden `lost+found` folder. Postgres refuses to initialize into a directory
that isn't empty, so it errored: *"directory exists but is not empty ... contains a
lost+found directory."*

**4b — lock-file shutdown loop.** Our first fix pointed `PGDATA` at a `/pgdata`
subfolder but kept the volume mounted at the data directory. That caused Postgres to
look for its lock file (`postmaster.pid`) at one path while its data lived at another,
so it declared the lock invalid and shut itself down every ~60 seconds.

**The clean final fix:** mount the volume **one level up** at `/var/lib/postgresql` and
set `PGDATA=/var/lib/postgresql/data`. Now:

- `lost+found` sits at `/var/lib/postgresql` (the mount root), and
- Postgres initializes cleanly into the empty `data/` *subdirectory*, and
- data and lock file live together under the path Postgres expects.

Because the dev database had no real data yet, we wiped and recreated the volume:

```bash
kubectl delete -f k8s/dev-postgres.yaml
kubectl delete pvc postgres-pvc -n cyopo-dev   # (already gone via the line above)
kubectl apply -f k8s/dev-postgres.yaml
```

After this, Postgres came up `1/1 Running` with **0 restarts** and stayed stable.

**The durable rule to remember:** *never mount a persistent volume directly as a
database's data directory — mount it one level above and let the data directory be a
subdirectory.* This applies to any database (Postgres, MySQL) on any persistent volume.

### Verifying the database works

```bash
kubectl exec -it -n cyopo-dev deploy/postgres -- psql -U postgres -d cyopo_db -c "SELECT version();"
```

*Purpose:* runs `psql` *inside* the pod (so you don't need it on your Mac), connects to
the `cyopo_db` database with the Secret's credentials, and prints the version. Returned
`PostgreSQL 17.10 ... aarch64` — proving (1) the pod runs, (2) the DB initialized with
the right credentials, and (3) it's running on your arm64 Graviton nodes.

---

## Stage 6.1b — Dev Redis (the fast cache)

### Purpose — in plain English

Redis is an in-memory data store. Your app uses it only for **rate limiting** —
counting how many requests an IP makes, to block abuse. That data is fine to lose on a
restart (the counters just reset), so Redis needs **no durable disk, no password, and no
complex setup** — making it far simpler than Postgres.

### The manifest (`k8s/dev-redis.yaml`) — just two objects

- **Deployment** running `redis:7-alpine` (Redis 7 on a tiny Alpine Linux base), port 6379.
- **Service** named `redis` (headless), giving the stable hostname `redis`.

No PVC (data is in memory, not persisted). No Secret (no password; it's only reachable
inside the cluster and holds throwaway data).

### Commands

```bash
kubectl apply -f k8s/dev-redis.yaml
kubectl get pods -n cyopo-dev
```

*Purpose:* creates Redis and checks it's running (it comes up in seconds — no disk to
wait for).

```bash
kubectl exec -it -n cyopo-dev deploy/redis -- redis-cli ping
```

*Purpose:* runs the Redis client inside the pod and pings the server. Returned `PONG`,
confirming Redis is up and responding.

### Analogy

If Postgres is the permanent records room (locked, durable, audited), Redis is the
whiteboard by the door — quick to scribble on, wiped clean without worry. You don't put
a vault door on a whiteboard.

---

## Stage 6.2 — Prod database (RDS) — what we did and why we DEFERRED it

### Purpose — in plain English

Production needs a *managed* database — one AWS runs and maintains, with automatic
backups and the ability to recover data to any point in time. This is **Amazon RDS**.
We chose RDS for prod (not an in-cluster pod) because prod holds billing, payment, and
GST-invoice records, which must be durable and recoverable. AWS handles the hard parts
(patching, backups, failover) for you.

### What we built (the free prep, left in place for launch)

Even though we deferred the database itself, we created two free supporting pieces that
will be reused at launch:

**1. DB subnet group (`cyopo-db-subnet-group`)** — tells RDS which subnets it may live
in. We chose the **two private subnets**, so the database sits in the hidden part of the
network, reachable from the cluster but never from the internet. (RDS requires two
Availability Zones even for a single instance, so it *could* fail over later.)
*Console:* RDS → Subnet groups → Create → pick `cyopo-vpc` + the two private subnets.

**2. Security group (`cyopo-rds-sg`)** — a firewall allowing inbound PostgreSQL
(port 5432) **only from the cluster's worker nodes**, nothing else.
*Command to find the nodes' security group:*

```bash
aws eks describe-cluster --region ap-south-1 --name cyopo-cluster \
  --query "cluster.resourcesVpcConfig.clusterSecurityGroupId" --output text
```

*Console:* EC2 → Security groups → Create → VPC `cyopo-vpc` → inbound rule:
type PostgreSQL, source = that cluster security group ID.

**Why reference the node security group instead of IP addresses:** node IPs change as
nodes are replaced or scaled; allowing the *security group* means any current/future
node is automatically permitted, with zero maintenance.

**Two layers of protection:** the security group controls who can *knock* (network
reachability); the password controls who can *enter* (authentication). Both are required.

### The RDS instance settings we DECIDED (to apply at launch)

We walked the entire "Create database → Full configuration" form and chose:

- **Engine:** PostgreSQL 17 (matches dev/local for migration parity).
- **Template / Availability:** Single-AZ (one instance) — cheaper; upgrade to Multi-AZ
  (automatic failover) later when downtime has real cost.
- **Instance class:** `db.t4g.micro` (Graviton, smallest sensible prod size). *We caught
  the form defaulting to a much larger class showing $74/mo and corrected it to ~$28/mo.*
- **Storage:** gp3, 20 GB, autoscaling enabled.
- **Identifier / user:** `cyopo-prod-db` / `cyopo_admin`, self-managed strong password.
- **Public access: No** — critical; the database is never exposed to the internet.
- **Security group:** `cyopo-rds-sg`; **Subnet group:** `cyopo-db-subnet-group`.
- **Initial database name: `cyopo_db`** — easy to miss; if blank, no database is created.
- **Backups:** 7-day automated (kept — the main reason to use RDS). **Encryption:** on
  (default key). **Database Insights:** Standard, not Advanced (Advanced is a paid tier).
  **Enhanced Monitoring / DevOps Guru / log exports:** off (paid extras, not needed yet).

### Why we DEFERRED creating it

Creating RDS now would cost ~$28/month for a database sitting idle while we're still
building (and developing entirely against the free dev database). So we **deferred** the
actual instance creation until just before go-live. The subnet group and security group
cost nothing and remain ready, making the eventual creation a ~10-minute paint-by-numbers
step. *(Also: if the AWS account is under 12 months old, Free Tier may make `db.t4g.micro`
nearly free — worth checking at launch.)*

---

## How we'll access the databases (pgAdmin via port-forward)

We wanted GUI access (pgAdmin) to both databases without exposing them to the internet.
The chosen method is **`kubectl port-forward`** — a secure tunnel from your laptop into
the cluster. Example for dev:

```bash
kubectl port-forward -n cyopo-dev svc/postgres 5433:5432
```

*Purpose:* forwards your laptop's `localhost:5433` into the dev Postgres service. pgAdmin
then connects to `localhost:5433` as if the database were local. The tunnel exists only
while the command runs and only for you. (Port 5433 avoids clashing with your local
Postgres on 5432.) Prod will use the same pattern once RDS exists.

**Why not make the database publicly accessible:** an internet-reachable database is
continuously scanned and attacked by bots worldwide; a strong password doesn't protect
against Postgres-protocol vulnerabilities — only *not being reachable* does. The tunnel
gives the same pgAdmin convenience with none of that exposure.

---

## Summary: what Step 6 achieved

| Stage             | What it achieved                                           | Cost                      |
|-------------------|------------------------------------------------------------|---------------------------|
| 6.0 Namespaces    | Created `cyopo-dev` / `cyopo-prod` partitions              | $0                        |
| 6.1a Dev Postgres | Postgres 17 in-cluster, durable EBS disk, addr `postgres`  | ~$0.50/mo (5GB disk)      |
| 6.1b Dev Redis    | Redis 7 in-cluster, addr `redis`                           | $0                        |
| 6.2 Prod RDS      | **Deferred**; subnet group + security group prepped (free) | $0 now, ~$28/mo at launch |

**End state:** the dev environment has a complete, working data layer; prod's database
is fully designed and its networking prepped, ready to stand up in minutes at launch.

**The big lessons worth keeping:**

1. Persistent storage on EKS needs three things present: a default StorageClass, the EBS
   CSI driver, and IAM permission for that driver (via Pod Identity).
2. Never mount a volume directly as a database's data directory — mount one level up.
3. Keep prod data private (no public access); reach it via port-forward, not open ports.
4. Defer paid resources (like RDS) until you actually need them; prep the free parts now.