# Step 9 — DNS + TLS (Public HTTPS on Your Domain): Full Walkthrough

This document explains everything we did in Step 9, assuming **no prior DNS or
networking knowledge**. It covers what DNS and TLS are, every console setting and
command, the gotchas we hit, and analogies throughout.

---

## Where Step 9 fits

By the end of Step 8, the app ran in the cloud but was only reachable through a private
`kubectl port-forward` tunnel — useless to real users. Step 9 makes it reachable from
**anywhere on the internet at a real web address, secured with HTTPS**:
`https://api-dev.cyopo.com`.

### Two concepts in plain English

**DNS (Domain Name System)** is the internet's phone book. People type a *name*
(`api-dev.cyopo.com`); computers need an *address* (an IP, or a load-balancer hostname).
DNS translates the name into the address. "Setting up DNS" means creating the phone-book
entry that says "`api-dev.cyopo.com` → my load balancer."

**TLS (the 's' in https)** encrypts traffic between the user's browser and your server,
and proves your site is really yours (the padlock). A **certificate** issued by a trusted
authority (Let's Encrypt, free) is what makes the padlock appear. Without it, browsers
show "Not secure" and data travels in the clear.

### The full chain we built

```
browser → DNS (Route 53) → NLB (load balancer) → NGINX Ingress → cyopo-api service → app
                                                    (+ TLS certificate from Let's Encrypt)
```

---

## Stage 9.1 — Route 53 hosted zone (AWS takes over DNS)

### What & why

A **hosted zone** is Route 53's container for all of a domain's DNS records. Creating one
for `cyopo.com` gives AWS a place to hold records like "`api-dev` → load balancer." We
chose Route 53 (over keeping DNS at Hostinger) so all DNS lives with AWS, which
integrates cleanly with load balancers and certificate validation.

### Console steps

Route 53 → Hosted zones → **Create hosted zone**:

- **Domain name:** `cyopo.com`
- **Type:** Public hosted zone
- **Create.**

AWS immediately created two records automatically:

- An **NS record** listing the **4 AWS nameservers** assigned to your zone.
- An **SOA record** (Start of Authority — administrative metadata; we don't touch it).

The 4 nameservers looked like:

```
ns-405.awsdns-50.com
ns-1511.awsdns-60.org
ns-652.awsdns-17.net
ns-2015.awsdns-59.co.uk
```

These are the "phone book branches" that will now answer questions about `cyopo.com` —
**once we tell the domain registrar (Hostinger) to use them** (next stage).

**Cost:** ~$0.50/month per hosted zone. Routing queries to AWS resources via alias
records is free.

---

## Stage 9.2 — Point Hostinger at Route 53 (nameserver change)

### What & why

The domain was *registered* at Hostinger, so Hostinger controls which nameservers the
world is told to use. To hand DNS to AWS, we change the domain's nameservers at Hostinger
to the 4 AWS ones. After this, every DNS lookup for `cyopo.com` goes to Route 53.

### Console steps (Hostinger hPanel)

Domains → `cyopo.com` → **DNS / Nameservers** → **Change nameservers** → enter the 4 AWS
nameservers → Save.

### The gotchas we hit (all real, all fixed)

1. **Trailing dots.** Route 53 displays nameservers with a trailing period
   (`ns-405.awsdns-50.com.`). Hostinger **rejects** the trailing dot ("Invalid nameserver
   structure"). Fix: remove the trailing dot from each.
2. **The two default parking nameservers.** Hostinger pre-fills `atlas.dns-parking.com`
   and `hyperion.dns-parking.com`. These had to be **removed** — only the 4 AWS ones
   should remain. Leaving them mixed in causes inconsistent resolution (some lookups go
   to AWS, some to Hostinger's parking servers that know nothing about your records).
3. **The empty first-two-fields trap.** After removing the parking entries, the top two
   "Nameserver 1/2" fields were left empty (required), blocking Save. Fix: fill all four
   AWS nameservers into the first four fields, no gaps, no duplicates. (Order doesn't
   matter for DNS.)

### Verifying the handoff

On the Mac:

```bash
dig +short NS cyopo.com
```

This returned the 4 `awsdns` nameservers (and nothing else) — confirming the world now
sees AWS as authoritative for `cyopo.com`. (DNS changes can take minutes to hours to
propagate; ours was fast.) Hostinger also showed "DNS is managed at another provider,"
which is the expected, correct state.

---

## Stage 9.3 — cert-manager ClusterIssuer (Let's Encrypt configuration)

### What & why

In Step 5 we installed cert-manager but didn't tell it *where* to get certificates. A
**ClusterIssuer** is that configuration: "obtain certificates from Let's Encrypt." Once
it exists, any Ingress we annotate gets a free, auto-renewing HTTPS certificate.

### How Let's Encrypt proves you own the domain (HTTP-01 challenge)

Before issuing a cert for `api-dev.cyopo.com`, Let's Encrypt must verify you control it.
The **HTTP-01 challenge**: Let's Encrypt asks for a specific file at
`http://api-dev.cyopo.com/.well-known/acme-challenge/...`; cert-manager serves it through
your NGINX ingress; Let's Encrypt fetches it, confirms control, and issues the cert. This
needs DNS to resolve the domain to your NLB first (which 9.1/9.2 set up).

### The file (`k8s/cluster-issuer.yaml`), line by line

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer                     # cluster-wide issuer (usable by any namespace)
metadata:
  name: letsencrypt-prod                # we reference this name in Ingresses
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory   # Let's Encrypt PRODUCTION (trusted certs)
    email: karthikarji22@gmail.com       # Let's Encrypt sends expiry notices here
    privateKeySecretRef:
      name: letsencrypt-prod-account-key # cert-manager stores your LE account key here (auto-created)
    solvers:
      - http01: # use the HTTP-01 challenge method
          ingress:
            class: nginx                  # solved through the NGINX ingress (from Step 5)
```

### Commands

```bash
kubectl apply -f k8s/cluster-issuer.yaml
kubectl get clusterissuer
```

The second confirmed `letsencrypt-prod` with **READY=True**, meaning cert-manager
registered an account with Let's Encrypt and is ready to issue certs.

**Note on rate limits:** Let's Encrypt's production endpoint limits failed attempts
(~5/hour per domain). For a clean single attempt this is fine; for heavy experimentation
people use the staging endpoint first. We went straight to prod and it worked.

---

## Stage 9.4 — DNS record + Ingress (connecting it all)

### Part A — Route 53 alias record (console)

First we fetched the NLB hostname:

```bash
kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

This printed `a4ecc...elb.ap-south-1.amazonaws.com` — your NGINX load balancer from Step 5.

Then Route 53 → Hosted zones → `cyopo.com` → **Create record**:

- **Record name:** `api-dev` (builds `api-dev.cyopo.com`)
- **Record type:** `A`
- **Alias:** ON
- **Route traffic to:** Alias to Network Load Balancer → ap-south-1 → select the NLB
- **Evaluate target health:** **No** ← important; with a single NLB, health-evaluation can
  intermittently fail to resolve the record (which would also break the ACME challenge).
- **Create.**

**Why an Alias A record (not a CNAME):** alias records are the AWS-recommended way to
point at AWS resources — they're free to query and resolve directly to the load balancer.

### Part B — The Ingress (`k8s/dev-ingress.yaml`), line by line

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: cyopo-api-ingress
  namespace: cyopo-dev
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod          # triggers cert-manager to get a cert
    nginx.ingress.kubernetes.io/proxy-body-size: "15m"        # allow 15MB uploads (Cloudinary)
spec:
  ingressClassName: nginx                # handled by the NGINX ingress controller
  tls:
    - hosts:
        - api-dev.cyopo.com               # request HTTPS for this host
      secretName: cyopo-api-tls           # cert-manager stores the issued cert in this Secret
  rules:
    - host: api-dev.cyopo.com             # for requests to this hostname...
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cyopo-api            # ...route to the cyopo-api service
                port:
                  number: 80
```

Key points:

- The `cert-manager.io/cluster-issuer` annotation is what makes cert-manager automatically
  request a Let's Encrypt cert for `api-dev.cyopo.com` and run the HTTP-01 challenge.
- `proxy-body-size: "15m"` is the **body-size fix planned back in Step 5** — NGINX defaults
  to a 1MB upload limit; your app allows 15MB (Cloudinary), so we raise it here to prevent
  `413 Request Entity Too Large` errors.
- The `rules` section is the actual routing: hostname `api-dev.cyopo.com` → service
  `cyopo-api` on port 80.

### Commands

```bash
kubectl apply -f k8s/dev-ingress.yaml
kubectl get certificate -n cyopo-dev -w
```

We watched `cyopo-api-tls` go from `READY=False` to **`READY=True`** within a few minutes
as cert-manager completed the Let's Encrypt challenge through NGINX.

---

## Stage 9.5 — Verification (end to end)

```bash
kubectl get certificate -n cyopo-dev      # cyopo-api-tls READY=True
dig +short api-dev.cyopo.com              # returned the NLB IPs
```

And in the browser:

- `https://api-dev.cyopo.com/swagger-ui/index.html` — loaded with a **trusted padlock**;
  Swagger's "Servers" dropdown showed `https://api-dev.cyopo.com`.
- `https://api-dev.cyopo.com/actuator/health` — returned `{"status":"UP"}` over HTTPS.

No certificate warning = a genuine, browser-trusted Let's Encrypt certificate, issued
automatically and set to auto-renew.

---

## What Step 9 achieved

| Item          | Result                                                                      |
|---------------|-----------------------------------------------------------------------------|
| DNS           | `cyopo.com` managed by Route 53; `api-dev.cyopo.com` → NLB (alias A record) |
| TLS           | Let's Encrypt cert `cyopo-api-tls` issued, READY=True, auto-renewing        |
| Public access | `https://api-dev.cyopo.com` reachable worldwide with a trusted padlock      |
| Upload limit  | NGINX raised to 15MB for Cloudinary uploads                                 |

**End state:** your dev backend is a real, public, HTTPS-secured API on your own domain —
no port-forward. The full request path (browser → Route 53 → NLB → NGINX → app → database)
works end to end.

**Durable lessons:** strip trailing dots and remove parking nameservers when moving DNS to
Route 53; use Alias A records (not CNAME) to AWS resources; turn off health-evaluation for
a single-NLB alias; and cert-manager + Let's Encrypt gives free auto-renewing HTTPS once
DNS resolves and the ingress can serve the HTTP-01 challenge.