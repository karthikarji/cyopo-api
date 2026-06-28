# Step 4 — Building the EKS Cluster: What, Why, and How (Console ‖ eksctl)

This document walks through every stage of Step 4 (building the EKS cluster by
hand in the AWS Console), explaining **what** we did, **why this stage exists at
all**, **how it connects to the earlier steps**, **what we achieved by it**, and
showing the **console action** and the **eksctl YAML equivalent** side by side.

It's written so that someone with no DevOps or AWS background can follow the
reasoning, not just the clicks.

---

## First: where Step 4 fits in the whole journey

Before this step, here is what we had already done:

- **Step 1** — packaged your application into a *container image* (a sealed box
  containing your app and everything it needs to run).
- **Step 2** — installed the tools and created a secure AWS login so your laptop
  can command AWS.
- **Step 3** — uploaded that container image into **ECR**, a private warehouse in
  the cloud. The image now lives in AWS, but *nothing is running it*.

Think of it like this: you've built a product (the app) and stored it in a
warehouse (ECR). But a product sitting in a warehouse does nothing. You need an
actual **place that runs it** — computers, connected to the internet, that can
fetch your image, run it, restart it if it crashes, and scale up under load.

**That "place that runs it" is the cluster. Step 4 builds it from nothing.**

A useful analogy for the whole step: we are building a **corporate campus** where
your application will work.

- The **VPC and subnets** (4.1) are the *land and the buildings* — a fenced plot
  with a public reception area facing the street and secure offices in the back.
- The **cluster control plane** (4.3) is the *building manager* — it doesn't do
  the physical work, but it decides who does what, and replaces workers who quit.
- The **node group** (4.5) are the *actual workers* — the computers that run your
  app.
- The **IAM roles** (4.2, 4.4) are *security badges* that grant the manager and
  the workers permission to operate inside your AWS account.

At the end of Step 4 you'll have a fully staffed, fully wired campus that is
*ready to run your app* — even though the app itself isn't deployed onto it yet
(that comes in later steps). This step builds the venue; later steps put your app
on the stage.

---

## The big picture: what a cluster is made of

An EKS cluster is five interlocking pieces, built in order because each depends
on the one before:

1. **A network (VPC + subnets + gateways)** for everything to live in.
2. **A cluster IAM role** so the control plane can act in AWS on your behalf.
3. **The cluster (control plane)** — the managed "brain" that schedules work.
4. **A node IAM role** so worker machines can join, pull images, and network.
5. **A node group (worker nodes)** — the actual machines that run your pods.

The control plane (the brain) and the nodes (the body) are deliberately
separate. You can have a healthy cluster with zero nodes — it just has nowhere to
run workloads until you add a node group.

---

## Stage 4.1 — The network foundation (VPC)

### Purpose — in plain English

Before you can run *any* computer in the cloud, that computer needs to live
inside a network — an address space where it has an IP, can be reached, and can
reach others. AWS doesn't let you just drop a machine "onto the internet"; you
first carve out your own private slice of cloud network, called a **VPC**
(Virtual Private Cloud). Everything else in this entire project — the cluster,
the worker machines, the database, the cache — will live *inside* this network.

**This is the foundation. Nothing before it depends on it; everything after it
sits inside it.** That's why it's the very first thing we built.

The clever part is dividing the network into two kinds of zones:

- a **public** zone that faces the internet (where the front-door load balancer
  will sit, so users can reach us), and
- a **private** zone hidden from the internet (where the actual app machines and
  database sit, so attackers can't reach them directly).

**What we achieved:** a secure, isolated network with a public "reception area"
and a private "back office," spread across two physically separate data centers
(Availability Zones) so a single data-center failure can't take us down.

### What & why (the settings)

- **A VPC** isolates your resources in their own network space.
- **Two AZs** give high availability — if one AZ's data center fails, the other
  survives. EKS requires at least two.
- **Public subnets** hold internet-facing things (the load balancer). They route
  to the internet *directly* via the internet gateway.
- **Private subnets** hold your actual workloads (nodes, later RDS and Redis).
  They reach *out* via the NAT gateway, but nothing on the internet reaches *in*.
- **One NAT gateway** (not one per AZ): a deliberate cost choice — ~$32/mo
  instead of ~$64/mo. Slightly less resilient, fine for launch.
- **Subnet tags** (`kubernetes.io/role/elb` on public, `internal-elb` on private)
  tell EKS where to place load balancers. Without them, load balancers silently
  fail to provision later — the most common EKS-by-hand trap.

### Analogy

The VPC is a fenced corporate campus. Public subnets are the reception and
parking lot visible from the street; private subnets are the secure offices
inside. The internet gateway is the front gate to the public road. The NAT
gateway is the mailroom: staff inside can send mail *out*, but strangers can't
walk *in* through it.

| Console action                           | eksctl YAML                                     |
|------------------------------------------|-------------------------------------------------|
| VPC → Create VPC → "VPC and more" wizard | `vpc:` block (eksctl builds it)                 |
| IPv4 CIDR `10.0.0.0/16`                  | `vpc.cidr: "10.0.0.0/16"`                       |
| Number of AZs: 2                         | `availabilityZones: [ap-south-1a, ap-south-1b]` |
| NAT gateways: "In 1 AZ"                  | `vpc.nat.gateway: Single`                       |
| 2 public + 2 private subnets             | (eksctl derives from AZs automatically)         |
| **Manually** add subnet tags             | **Automatic** — eksctl tags subnets for you     |

**Key difference:** the manual subnet tagging — the error-prone bit — is the
thing eksctl does automatically. The strongest argument for eksctl over the
console at the networking layer.

---

## Stage 4.2 — The cluster IAM role

### Purpose — in plain English

In the next stage we create the cluster's "brain" (control plane). That brain is
a piece of AWS-managed software that will need to *do things in your AWS account*
on your behalf — for example, plug network cables (create network interfaces),
set up load balancers, and write logs. AWS will not let any software do those
things unless it has been *granted permission*.

The way you grant a piece of software permission in AWS is by creating an **IAM
role** — essentially a security badge — and saying "the EKS service is allowed to
wear this badge." So **this stage exists purely to prepare the permission badge
that the cluster (built in 4.3) will wear.** We make the badge before we hire the
manager who wears it.

**How it connects:** it's a prerequisite for 4.3 — the cluster creation screen
will ask us to pick this role. **What we achieved:** a permission slip ready for
the control plane.

### What & why (the settings)

The control plane needs permission to act in your AWS account — create network
interfaces, manage load balancers, write logs. A role has two halves: a **trust
policy** (who may wear the badge — here, only the EKS service) and **permissions**
(what the badge allows — `AmazonEKSClusterPolicy`). This differs from your IAM
*user*: a user is who *you* log in as; a role is what a *service* temporarily
becomes.

### Analogy

You're about to hire a building manager. Before they start, you issue them a
keycard programmed with exactly the access they need — and you program the card so
that *only that manager role* can use it, nobody else.

| Console action                                  | eksctl YAML             |
|-------------------------------------------------|-------------------------|
| IAM → Create role → AWS service → EKS - Cluster | *(none — auto-created)* |
| Attach `AmazonEKSClusterPolicy`                 | *(none — auto-created)* |
| Name `cyopo-eks-cluster-role`                   | *(none — auto-created)* |

**Key difference:** eksctl **auto-creates** the cluster role, so there's no YAML
for it. Building by hand, you create and understand it explicitly — one of the
two things eksctl "hides."

---

## Stage 4.3 — The cluster (control plane)

### Purpose — in plain English

This is the heart of the whole step: we create the **control plane**, the
"manager brain" of Kubernetes. This is the thing that, once your app is deployed,
will decide *which worker machine* runs each copy of your app, will *restart*
your app if it crashes, will *move* it to a healthy machine if a machine dies,
and will *scale* it up when traffic rises. It is the always-on coordinator.

Crucially, the control plane does **not** run your app itself — it's the foreman,
not the bricklayer. After this stage you'll have a working brain with **no body
yet** (no worker machines). That's expected; the body comes in 4.5.

**How it connects:** it lives *inside* the network from 4.1, and it wears the
permission badge from 4.2 (the creation screen asks for both). **What we
achieved:** the orchestrator now exists and is "Active" — but it has nothing to
run workloads on until we add nodes.

This is also the stage where the **~$73/month control-plane fee begins** — you're
paying for AWS to run and maintain this managed brain for you 24/7.

### What & why (the settings)

- **Custom config, not Auto Mode:** Auto Mode manages nodes for you but costs
  more and removes the node-group control we want (and the learning).
- **Standard support:** free; supports the version 14 months then auto-upgrades.
- **"Allow cluster administrator access":** grants the IAM principal creating the
  cluster (your `cyopo-admin` user) Kubernetes admin rights — without it you'd
  build a cluster you couldn't administer (a classic lockout).
- **EKS API auth mode:** the modern, cleaner authentication source.
- **Public and private endpoint:** you can reach the cluster's API from your
  laptop (public) while internal traffic stays in the VPC (private).
- **All four subnets:** the control plane spreads its network interfaces across
  AZs for resilience.

### Analogy

You've hired the foreman. They coordinate everything and never go home, but they
don't personally lay bricks. Right now they're standing in an empty campus with no
crew — ready to direct work the moment workers arrive.

| Console action                            | eksctl YAML                                               |
|-------------------------------------------|-----------------------------------------------------------|
| Name `cyopo-cluster`                      | `metadata.name: cyopo-cluster`                            |
| Region Mumbai                             | `metadata.region: ap-south-1`                             |
| K8s version 1.35                          | `metadata.version: "1.35"`                                |
| Cluster IAM role = cyopo-eks-cluster-role | *(eksctl makes its own)*                                  |
| Endpoint "Public and private"             | `vpc.clusterEndpoints: {publicAccess, privateAccess}`     |
| Auth mode "EKS API"                       | `accessConfig.authenticationMode: API`                    |
| "Allow cluster administrator access"      | (default `bootstrapClusterCreatorAdminPermissions: true`) |
| Add-ons                                   | `addons:` list                                            |

---

## Stage 4.4 — The node IAM role

### Purpose — in plain English

In the next stage we add the real worker machines. Those machines need to do
three specific things, and AWS requires permission for each:

1. **Clock in** — register themselves with the cluster brain so it knows they
   exist and can send them work.
2. **Fetch your app** — reach into the **ECR warehouse from Step 3** and pull
   down your container image. *This is the direct payoff of Step 3:* the image you
   pushed there is useless unless the workers are allowed to pull it.
3. **Get networking** — obtain IP addresses for the app copies they run.

So **this stage exists to prepare a second permission badge — this one for the
worker machines** (different from the manager's badge in 4.2).

**How it connects:** it links back to Step 3 (this is *how the warehoused image
gets retrieved*) and forward to 4.5 (the node group will wear this badge). **What
we achieved:** a permission slip that lets workers join, pull the app, and network.

### What & why (the settings)

The worker nodes are EC2 instances, so the badge is trusted by **EC2** (not EKS —
that's the difference from 4.2). The three attached policies map exactly to the
three jobs above:

- `AmazonEKSWorkerNodePolicy` → register with and talk to the cluster
- `AmazonEC2ContainerRegistryReadOnly` → **pull images from ECR** (your image)
- `AmazonEKS_CNI_Policy` → manage pod networking (IP assignment)

Miss any one and nodes either won't join or can't pull images.

### Analogy

Before the work crew arrives, you issue them staff badges that let them clock in,
collect materials from the warehouse, and use the campus utilities.

| Console action                        | eksctl YAML                                         |
|---------------------------------------|-----------------------------------------------------|
| IAM → Create role → AWS service → EC2 | *(none — auto-created)*                             |
| Attach the 3 policies above           | *(eksctl auto-attaches the same 3)*                 |
| Name `cyopo-eks-node-role`            | optional: `managedNodeGroups[].iam.instanceRoleARN` |

**Key difference:** like the cluster role, eksctl auto-creates the node role. The
YAML shows a commented `instanceRoleARN:` line to reuse your hand-made role
instead. The second thing eksctl "hides."

---

## Stage 4.5 — The node group (worker nodes)

### Purpose — in plain English

This is where the cluster finally gets a **body**. A node group is a set of real
computers (EC2 virtual machines) that join the cluster and become the muscle that
actually runs your application. Until now the cluster brain had nobody to command;
now it has a crew.

These machines will: join the cluster (using the badge from 4.4), and stand ready
to pull your image from ECR (Step 3) and run it the moment you deploy. We put them
in the **private** part of the network (from 4.1) so they're hidden from the
internet — users will never talk to them directly; they'll only ever talk to a
load balancer out front (built in a later step), which then forwards traffic in.

**How it connects:** it ties together almost everything before it — it lives in
the private subnets (4.1), wears the worker badge (4.4), reports to the control
plane (4.3), and will pull the image from ECR (Step 3). **What we achieved:** the
cluster is now *complete and capable of running workloads*. The venue is staffed.

This is also where the **node compute cost** begins (~$30–50/mo for two
`t4g.medium` machines) — you're now paying for the actual worker computers.

### What & why (the settings)

- **arm64 AMI (Graviton):** matches the arm64 image you built on Apple Silicon,
  and Graviton is cheaper. The AMI type is **locked at creation**.
- **t4g.medium:** Graviton family, 2 vCPU / 4 GB — enough for your app plus system
  add-ons (NGINX, cert-manager). `t4g.small` would be tight.
- **On-Demand:** predictable; Spot (cheaper) is better added later, esp. for dev.
- **2 / 2 / 3 scaling:** two nodes spread pods across both AZs and survive a
  single node failure; min 2 keeps both AZs covered; max 3 gives headroom.
- **Private subnets only:** nodes must not be directly reachable from the
  internet. They reach out via NAT; inbound traffic arrives only through the load
  balancer in the public subnets.

### Analogy

The work crew arrives and clocks in. They're stationed in the secure back offices
(private subnets), not the public reception — visitors never meet them directly.
They're ready to start building the moment the foreman hands out work.

| Console action                        | eksctl YAML                                      |
|---------------------------------------|--------------------------------------------------|
| Node group name `cyopo-nodes`         | `managedNodeGroups[].name: cyopo-nodes`          |
| AMI `AL2023_ARM_64_STANDARD`          | `amiFamily: AmazonLinux2023` + t4g instance      |
| Instance type `t4g.medium`            | `instanceType: t4g.medium`                       |
| Capacity "On-Demand"                  | `spot: false`                                    |
| Scaling 2 / 2 / 3                     | `desiredCapacity: 2`, `minSize: 2`, `maxSize: 3` |
| Disk 20 GB                            | `volumeSize: 20`                                 |
| **Select only the 2 private subnets** | **`privateNetworking: true`**                    |
| Node IAM role = cyopo-eks-node-role   | (optional `iam.instanceRoleARN`)                 |

### The debugging lesson (the failure we hit and fixed)

The first node-group attempt **failed** with:

```
Ec2SubnetInvalidConfiguration: One or more Amazon EC2 Subnets [subnet-061f8aa133f0f06ce]
... does not automatically assign public IP addresses ...
```

**Cause:** a **public** subnet (`public1`) was selected for the nodes. Nodes
belong in **private** subnets. EKS complained that the public subnet didn't
auto-assign public IPs — but the real fix wasn't "enable public IPs," it was
"don't put nodes in the public subnet at all."

**Why this matters (plain English):** putting worker machines in a public subnet
would expose them directly to the internet — the opposite of what we want. The
error was AWS protecting us from a misconfiguration.

**Fix:** failed managed node groups can't be edited (subnets are immutable after
creation), so we **deleted and recreated** the node group selecting **only the two
private subnets**. The nodes then launched and joined; the `Pending` CoreDNS and
metrics-server pods scheduled onto them and went `Running`.

**In eksctl, this whole class of mistake is prevented by one line:**
`privateNetworking: true`.

---

## Connecting kubectl (after nodes exist)

### Purpose — in plain English

Everything so far was done by talking to *AWS*. But the cluster runs *Kubernetes*,
which has its own language and its own command-line tool, `kubectl`. This final
action points `kubectl` at your new cluster so you can look inside it and command
it directly from your laptop — see the worker machines, see what's running, and
(later) deploy your app.

**What we achieved:** a working remote control for the cluster.

```bash
aws eks update-kubeconfig --region ap-south-1 --name cyopo-cluster
kubectl get nodes      # expect 2 nodes, status Ready
kubectl get pods -A    # expect CoreDNS, kube-proxy, aws-node, etc. Running
```

It writes the cluster's address, certificate, and login method into your laptop's
`~/.kube/config`. No eksctl equivalent is needed — `eksctl create cluster` does
this automatically at the end.

---

## Summary: what we built, and what eksctl hides vs. exposes

By the end of Step 4 we went from "an app image sitting in a warehouse" to "a
live, secure, multi-machine cloud environment that is ready to run that app." The
venue is built and staffed; later steps open the doors and put the app on stage.

| Step-4 stage         | What it achieved                       | In the YAML?                |
|----------------------|----------------------------------------|-----------------------------|
| 4.1 VPC/subnets/tags | The secure network everything lives in | ✅ Yes (tags auto-done)      |
| 4.2 Cluster IAM role | Permission badge for the control plane | ❌ No (auto-created)         |
| 4.3 Cluster          | The manager "brain" (no body yet)      | ✅ Yes                       |
| 4.4 Node IAM role    | Permission badge for the workers       | 🟡 Optional                 |
| 4.5 Node group       | The worker machines (the body)         | ✅ Yes (`privateNetworking`) |

**The core insight from doing it both ways:** the console forces you to create
every IAM role and every subnet tag explicitly, so you see and understand the
moving parts. eksctl creates those for you behind the scenes, trading visibility
for reliability and speed. Knowing both means you understand *what eksctl is doing
on your behalf* — which is exactly the point of the exercise.