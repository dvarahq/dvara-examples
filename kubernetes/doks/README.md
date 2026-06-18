# DigitalOcean Kubernetes (DOKS)

Deploy DVARA AI Gateway on [DOKS](https://www.digitalocean.com/products/kubernetes) with **DO Managed PostgreSQL** (direct connection — keeps config hot-reload working), a plain Kubernetes Secret, and **Traefik v3** ingress with built-in Let's Encrypt TLS.

This is the cloud-agnostic dvara Helm chart (`oci://ghcr.io/dvarahq/charts/dvara`) with a thin DO overlay — **no DOKS-specific chart code**. It differs from the [GKE recipe](../gke/) in just three places: no Workload Identity (DO has none — secrets are a plain k8s Secret), DO Managed PG instead of Cloud SQL, and Traefik instead of GCE Ingress. See [../README.md](../README.md) for the shared chart reference.

> **DOKS ≠ App Platform.** DVARA also has a [DigitalOcean **App Platform**](../../digitalocean/) (PaaS) recipe — that's the no-Kubernetes path. This folder is for **managed Kubernetes** (DOKS).

| File | What it is |
|---|---|
| **[deploy.sh](deploy.sh)** | End-to-end scripted path: `doctl` cluster → DO Managed PG → Traefik v3 → secrets → `helm install` → verify. Idempotent-ish. |
| **[values-doks.yaml](values-doks.yaml)** | The DO overlay — DO Managed PG DSN via `extraEnv` (password via `secretKeyRef`), Traefik ingress + Let's Encrypt, plain-Secret `existingSecret`. |

## Secrets (five platform secrets + the DB password)

Each generated with `openssl rand -base64 32` except the license. `deploy.sh` mints them and stores them as a plain k8s Secret (DO has no Secret Manager / Workload Identity equivalent — if you run an external secrets manager like Vault or Doppler, point `secrets.existingSecret` at its synced Secret instead).

| Secret (k8s key) | Purpose |
|---|---|
| `enterprise-license-key` | Signed `DVARA-…` envelope; validated at boot |
| `gateway-encryption-master-password` | AES-256-GCM key for `ENCRYPTED`-mode credentials — **loss is unrecoverable, escrow offline** |
| `gateway-server-api-key` | Bearer for `/actuator/gateway-status` |
| `gateway-metrics-api-key` | Bearer for `/actuator/prometheus` — **must differ** from the above |
| `audit-hmac-secret` | HMAC-SHA256 key signing the audit chain |

Provider keys are **not** env vars — DVARA uses BYOK (tenant keys live AES-encrypted in the DB, added via the Console after install).

## Prerequisites

- [`doctl`](https://docs.digitalocean.com/reference/doctl/how-to/install/) authenticated (`doctl auth init`), plus `kubectl`, `helm` 3.8+, `openssl`
- A production-signed `DVARA_LICENSE_KEY`
- A domain for Let's Encrypt — `api.$BASE_DOMAIN` + `flightdeck.$BASE_DOMAIN` will point at the DO Load Balancer

## Deploy

```bash
export DVARA_LICENSE_KEY='DVARA-...'
export BASE_DOMAIN=dvara.example.com
export ACME_EMAIL=ops@example.com
./deploy.sh
```

The script prints the DO Load Balancer IP at the end. Point both A-records at it; Traefik issues the Let's Encrypt certs within a few minutes of DNS resolving.

## Database — direct connection, not a transaction pool

The DSN points at the DO Managed PG **private host on port 25060** with `sslmode=require`. DVARA propagates config (routes, policies) from Flightdeck to the data plane over PostgreSQL `LISTEN`/`NOTIFY` — that needs a **direct or session-level** connection.

:::warning
If you front the database with a **DO Connection Pool**, set it to **session** mode. A **transaction-mode** pool silently breaks `NOTIFY`, and config hot-reload stops working — it's the failure mode most specific to managed-DO. The direct `:25060` connection (what this recipe uses) is always safe.
:::

For stronger transport security than `sslmode=require`, download the DO CA cert and switch to `sslmode=verify-full` with the CA mounted.

## Ingress — Traefik v3, not ingress-nginx

`ingress-nginx` reached **end of life in March 2026**, so this recipe uses **Traefik v3**, which has a built-in ACME client (no separate cert-manager). The chart renders a standard `Ingress` with `ingress.className: traefik` and `baseDomain`-derived hosts; Traefik's `letsencrypt` certresolver issues the certs. The chart is controller-agnostic — to use a different controller, change `ingress.className` + `annotations` in `values-doks.yaml`.

## Visual path

- **[k9s](https://k9scli.io/)** — `k9s -n dvara` for a live pod/log/rollout dashboard.
- **DigitalOcean Console** — Kubernetes → your cluster shows nodes + workloads; Databases shows the PG instance + trusted sources; Networking → Load Balancers shows the Traefik LB and its health.
- **[Lens](https://k8slens.dev/)** — desktop GUI alternative to k9s.

## Verify

```bash
kubectl -n dvara get pods                                    # all Ready
kubectl -n dvara port-forward svc/dvara-flightdeck 8090:8090 &
open http://localhost:8090/                                  # /setup → tenant + API key
```

**Config hot-reload (the acceptance check):** create or edit a route in the Console, fire a request through the gateway, and confirm the new routing takes effect **without restarting any pod** — proof the data plane picked up the change over `NOTIFY`/`LISTEN` on the direct `:25060` connection.

## Pinning the image tag

`deploy.sh` and `values-doks.yaml` default to a published GA tag (`1.0.1`). Never `:latest` in production (non-reproducible, no clean rollback). Browse tags at the [GHCR package page](https://github.com/orgs/dvarahq/packages?repo_name=dvara); set `IMAGE_TAG` / `CHART_VERSION` to match (e.g. `1.1.0` once cut). Don't pin a tag that hasn't shipped — pods fail with `ImagePullBackOff`.

## Related

- **[../gke/](../gke/)** — the GKE recipe (Cloud SQL + Workload Identity + GCE Ingress)
- **[../README.md](../README.md)** — generic chart reference (single-tenant, multi-region)
- **[../../digitalocean/](../../digitalocean/)** — DO **App Platform** (PaaS, no Kubernetes)
- **[../../getting-started/](../../getting-started/)** — first-request scripts
