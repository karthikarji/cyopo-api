# Step 8 — Deploying the Application to Dev: Full Walkthrough

This document explains everything we did in Step 8, assuming **no prior Kubernetes
knowledge**. It covers the purpose, every file and command with line-by-line meaning,
the two debugging chains we hit (and what they taught), and analogies throughout.

---

## Where Step 8 fits

By the end of Step 7, the dev environment had everything an application needs *except
the application itself*: a database (Postgres pod), a cache (Redis pod), and the config

+ secrets (ConfigMap + Secret). Step 8 is where **your actual app finally runs in the
  cloud**, wired to all of those.

### Analogy

Steps 4–7 built and staffed a restaurant: the building (cluster), the front door
(ingress), the kitchen equipment (database, cache), and the recipe binder + safe
(config + secrets). Step 8 is **opening night** — the chef (your app) walks in, is handed
the recipes and safe combination, and starts cooking. And like a real opening night, a
couple of things went wrong the first time and had to be fixed live.

---

## What we deployed: two Kubernetes objects

The app is deployed as two objects in one file (`k8s/dev-app.yaml`), separated by `---`:

1. **Deployment** — runs your `cyopo-api` container and keeps it alive.
2. **Service** — gives the running app a stable internal address (`cyopo-api`).

### The Deployment, line by line

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cyopo-api
  namespace: cyopo-dev
spec:
  replicas: 1                         # run ONE copy of the app
  selector:
    matchLabels:
      app: cyopo-api                  # this Deployment manages pods labelled app=cyopo-api
  template: # the recipe for each pod:
    metadata:
      labels:
        app: cyopo-api                # label applied to the pod (matches selector above)
    spec:
      containers:
        - name: cyopo-api
          image: 423623871239.dkr.ecr.ap-south-1.amazonaws.com/cyopo-api:v1
            # the EXACT image in ECR (from Step 3). Nodes can pull it
          # because the node IAM role has ECR-ReadOnly (Step 4.4).
          ports:
            - containerPort: 8080      # the app listens on 8080 (Spring Boot default we set)
          envFrom: # THE STEP 7 PAYOFF: inject all config + secrets as env vars
            - configMapRef:
                name: cyopo-config     # all non-secret keys become environment variables
            - secretRef:
                name: cyopo-secrets    # all secret keys become environment variables
          resources:
            requests: # what the pod is GUARANTEED (used for scheduling)
              memory: "512Mi"
              cpu: "250m"              # 250m = 0.25 of a CPU core
            limits: # the ceiling the pod may NOT exceed
              memory: "1Gi"
              cpu: "1000m"             # 1 full core
          startupProbe: # "has the app finished starting?" — see debugging below
            httpGet:
              path: /actuator/health
              port: 8080
            failureThreshold: 30       # allow 30 failures...
            periodSeconds: 10          # ...checked every 10s = up to 5 min to start
            timeoutSeconds: 5          # wait up to 5s for each health response (THE FIX)
          readinessProbe: # "is the app ready to receive traffic right now?"
            httpGet:
              path: /actuator/health
              port: 8080
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe: # "is the app still alive, or hung and needs restart?"
            httpGet:
              path: /actuator/health
              port: 8080
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
            initialDelaySeconds: 60    # don't even start liveness checks for 60s (app boots ~40s)
```

**The three probes explained simply** (this matters because they caused our first bug):

- **startupProbe** — runs *first*, during boot. While it's failing, the other two are
  paused. It's the "is it up yet?" check with a generous window for slow starts.
- **readinessProbe** — after startup, decides whether to send traffic to the pod. If it
  fails, the pod stays alive but receives no traffic (shows `0/1` ready).
- **livenessProbe** — after startup, decides whether to *restart* a hung pod. If it
  fails repeatedly, Kubernetes kills and recreates the pod.

### The Service, line by line

```yaml
apiVersion: v1
kind: Service
metadata:
  name: cyopo-api
  namespace: cyopo-dev
spec:
  selector:
    app: cyopo-api                     # send traffic to pods labelled app=cyopo-api
  ports:
    - port: 80                         # the Service listens on port 80
      targetPort: 8080                 # and forwards to the app's port 8080
  type: ClusterIP                      # internal-only address (not exposed to internet yet)
```

`ClusterIP` means the app is reachable *inside* the cluster at the name `cyopo-api`, but
not from the internet. (Step 9's Ingress is what later exposes it publicly.) For Step 8
we reached it via port-forward to test.

---

## The commands we ran

```bash
kubectl apply -f k8s/dev-app.yaml
```

*Purpose:* creates both the Deployment and Service. Output confirmed
`deployment.apps/cyopo-api created` and `service/cyopo-api created`.

```bash
kubectl get pods -n cyopo-dev -w
```

*Purpose:* watches (`-w`) the pod as it progresses: `ContainerCreating` (pulling the
image) → `Running 0/1` (started but not yet ready) → ideally `1/1 Running`.

```bash
kubectl logs -n cyopo-dev deploy/cyopo-api -f
```

*Purpose:* follows (`-f`) the application's own logs — where we see Spring Boot start,
Flyway run migrations, and `Started CyopoApiApplication`.

```bash
kubectl exec -it -n cyopo-dev deploy/postgres -- psql -U postgres -d cyopo_db -c "\dn"
```

*Purpose:* runs `psql` *inside the Postgres pod* and lists schemas (`\dn`). This was the
decisive proof the app worked: it showed `auth`, `billing`, `portfolio`, `template` —
the schemas **Flyway built** when the app first connected. If the app hadn't connected
to the database, these wouldn't exist.

```bash
kubectl port-forward -n cyopo-dev svc/cyopo-api 8090:80
```

*Purpose:* opens a private tunnel from your laptop's `localhost:8090` to the app's
Service. Lets you test the app (health, Swagger) before it's publicly exposed.

---

## Debugging chain 1 — the probe-timeout restart loop

**Symptom:** the pod showed `0/1 Running` with a restart count, even though hitting
`/actuator/health` by hand returned `{"status":"UP"}`.

**The decisive clue** came from:

```bash
kubectl describe pod -n cyopo-dev -l app=cyopo-api | grep -A 5 -i "warning\|unhealthy\|probe"
```

which showed:

```
Startup probe failed: ... context deadline exceeded (Client.Timeout exceeded while awaiting headers)
```

**Why it happened:** the app takes ~40 seconds to start, and its `/actuator/health`
endpoint pings the database and Redis — which can take **more than 1 second** to
respond. Kubernetes probes default to `timeoutSeconds: 1`. So the probe gave up after 1
second, decided the app had failed, and restarted it — in a loop. A manual `curl`
(untimed) succeeded because you weren't enforcing a 1-second limit.

**The fix:** add `timeoutSeconds: 5` (and `failureThreshold`, `initialDelaySeconds`) to
the probes, giving the health check room to respond. This was a **manifest-only change**
— no rebuild needed:

```bash
kubectl apply -f k8s/dev-app.yaml
kubectl rollout restart deployment cyopo-api -n cyopo-dev
```

After this the pod reached `1/1 Running`, 0 restarts, and stayed stable.

**The lesson:** Spring Boot apps boot slowly and their health checks touch external
services. Kubernetes' default 1-second probe timeout is too aggressive for them. Always
set generous probe timeouts for Spring Boot. *This was NOT an auth problem* — the
security config was already correct; it was purely probe timing.

---

## Debugging chain 2 — the Swagger `/v3/api-docs` 500 error

**Symptom:** Swagger UI loaded but showed "Failed to load API definition," and
`/v3/api-docs` returned HTTP 500.

**Finding the cause:** the HTTP response only said "An unexpected error occurred" (the
app's global exception handler hiding details). The real cause was in the logs:

```
Caused by: java.lang.NoSuchMethodError:
  'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
  at org.springdoc.core...springdoc-openapi-starter-common-2.6.0.jar
```

**Why it happened:** a **dependency version mismatch**. `NoSuchMethodError` means a
library tried to call a method that doesn't exist at runtime. springdoc **2.6.0** was
built against an older Spring (6.1.x), but Spring Boot 3.4.5 ships Spring **6.2.10**,
which changed that method. So springdoc compiled fine but crashed when generating the
API docs. **Not a bug in your code** — the library versions simply didn't agree.

**The fix:** bump springdoc to **2.7.0** (compatible with Spring Boot 3.4.x) in `pom.xml`.
Because this is a code/dependency change, it required the **full rebuild → redeploy
cycle** — our first one by hand:

```bash
# 1. edit pom.xml: springdoc 2.6.0 -> 2.7.0
# 2. rebuild the image as a NEW immutable tag (v2, not overwriting v1):
docker build -t cyopo-api:v2 .
# 3. authenticate + tag + push to ECR:
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 423623871239.dkr.ecr.ap-south-1.amazonaws.com
docker tag cyopo-api:v2 423623871239.dkr.ecr.ap-south-1.amazonaws.com/cyopo-api:v2
docker push 423623871239.dkr.ecr.ap-south-1.amazonaws.com/cyopo-api:v2
# 4. update k8s/dev-app.yaml image tag v1 -> v2, then:
kubectl apply -f k8s/dev-app.yaml
kubectl rollout status deployment cyopo-api -n cyopo-dev
```

`kubectl rollout status` waits and reports when the new version is fully live. Kubernetes
does a **rolling update**: starts the v2 pod, waits for it to be healthy, then removes
the v1 pod. After this, `/v3/api-docs` returned real JSON and Swagger UI loaded.

**The lessons:**

1. Keep springdoc and Spring Boot versions compatible (version drift causes runtime
   `NoSuchMethodError`, even though compilation succeeds).
2. This hand-cycle — **change code → build → push immutable-tagged image → roll out** —
   is exactly what CI/CD (Step 13) automates. The immutable `v2` tag (vs overwriting
   `v1`) is what makes rollbacks and promotion trustworthy.

---

## What Step 8 achieved

| Item                        | Result                                                                  |
|-----------------------------|-------------------------------------------------------------------------|
| App deployed to `cyopo-dev` | `1/1 Running`, 0 restarts                                               |
| Database schemas            | Flyway built `auth`, `billing`, `portfolio`, `template` in dev Postgres |
| Health                      | `/actuator/health` returns UP                                           |
| API docs                    | Swagger UI + `/v3/api-docs` working (after springdoc fix)               |
| Image                       | running `cyopo-api:v2` (rebuilt with springdoc 2.7.0)                   |

**End state:** your application runs in the cloud, connected to the cloud database and
cache, configured entirely by Kubernetes — reachable via port-forward (public exposure
comes in Step 9).

**Durable lessons:** set generous probe timeouts for Spring Boot; keep library versions
compatible; and the build→push→rollout cycle with immutable tags is the foundation of
safe deployments.