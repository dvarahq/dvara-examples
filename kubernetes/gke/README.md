# Google Kubernetes Engine (GKE)

Deploy DVARA AI Gateway on [GKE](https://cloud.google.com/kubernetes-engine) with **Cloud SQL** (managed PostgreSQL, private IP — a direct connection that keeps config hot-reload working), **Workload Identity**, and a **GCE Ingress** fronted by a Google-managed TLS certificate.

This is the cloud-agnostic dvara Helm chart (`oci://ghcr.io/dvarahq/charts/dvara`) with a thin GCP overlay — **no GKE-specific chart code**. The chart already exposes every knob GKE needs: per-component `serviceAccount.annotations` (Workload Identity), `extraEnv` (the Cloud SQL DSN), `ingress.className`/`annotations` (GCE), and `secrets.existingSecret`. See [../README.md](../README.md) for the shared single-tenant / multi-region reference values.

| File | What it is |
|---|---|
| **[deploy.sh](deploy.sh)** | End-to-end scripted path: APIs → VPC-native cluster → Cloud SQL (private IP) → Workload Identity → secrets → `helm install` → verify. Idempotent-ish; re-runs resume. |
| **[values-gke.yaml](values-gke.yaml)** | The GCP overlay — pinned WI service-account names, GCE Ingress, Cloud SQL DSN via `extraEnv` (password via `secretKeyRef`). |
| **[managed-certificate.yaml](managed-certificate.yaml)** | The `ManagedCertificate` CRD for Google-managed TLS (used when you set a domain). |
| **[secretproviderclass.yaml](secretproviderclass.yaml)** | Optional — the Secret Manager → k8s Secret projection for the hardened "Workload Identity for secrets" path. |

## Secrets (five platform secrets + the DB password)

Each generated with `openssl rand -base64 32` except the license. `deploy.sh` mints them for you.

| Secret | Purpose | Notes |
|---|---|---|
| `DVARA_LICENSE_KEY` | Signed `DVARA-…` license envelope | Production-signed; from your DVARA account team. Validated at boot. |
| `DVARA_ENCRYPTION_MASTER_PASSWORD` | AES-256-GCM key for `ENCRYPTED`-mode provider credentials | **Loss is unrecoverable.** Escrow offline. |
| `DVARA_ACTUATOR_API_KEY` | Bearer for `/actuator/gateway-status` | flightdeck's status probe. |
| `DVARA_ACTUATOR_METRICS_API_KEY` | Bearer for `/actuator/prometheus` | **Must differ** from the above. |
| `DVARA_AUDIT_HMAC_SECRET` | HMAC-SHA256 key signing the audit chain | A mismatch across pods breaks the chain; production profiles refuse the dev placeholder. |

Provider keys are **not** env vars here — DVARA uses the BYOK model (tenant keys live AES-encrypted in `dvara_main.provider_credentials`, added via the Console after install).

## Prerequisites

- `gcloud` authenticated against a billing-enabled project, plus `kubectl`, `helm` 3.8+, `openssl`
- A production-signed `DVARA_LICENSE_KEY`
- (For HTTPS) a domain you can point A-records at — e.g. `dvara.example.com` → `api.…` + `flightdeck.…`

## Deploy

```bash
export PROJECT_ID=my-gcp-project
export DVARA_LICENSE_KEY='DVARA-...'
export BASE_DOMAIN=dvara.example.com    # omit for the IP-only smoke
./deploy.sh
```

The script prints the Ingress static IP at the end. Point `api.$BASE_DOMAIN` and `flightdeck.$BASE_DOMAIN` at it; the Google-managed cert provisions 15–60 min after DNS resolves (`kubectl -n dvara describe managedcertificate dvara-cert`).

**Quick IP smoke (no domain):** omit `BASE_DOMAIN`. The script drops the managed-cert annotation, allows HTTP, and sets the Ingress host to the raw static IP — fastest "does DVARA boot on GKE?" answer. Not for production (no TLS).

## Visual path

You don't have to drive this from the CLI:

- **[k9s](https://k9scli.io/)** — `k9s -n dvara` gives a live terminal dashboard of pods, logs, and rollout status; press `l` on a pod to tail logs while it boots.
- **GKE Console → Workloads** — [console.cloud.google.com/kubernetes/workload](https://console.cloud.google.com/kubernetes/workload) shows the three Deployments going green, plus the Ingress/LB and the ManagedCertificate status under **Services & Ingress**.
- **Cloud SQL Console** — confirms the instance is `RUNNABLE` with a private IP and no public IP.
- **[Lens](https://k8slens.dev/)** — a desktop GUI alternative to k9s if you prefer a full IDE view of the cluster.

## Verify

```bash
# All pods Ready:
kubectl -n dvara get pods

# Console (port-forward bypasses the LB/cert while DNS propagates):
kubectl -n dvara port-forward svc/dvara-flightdeck 8090:8090
open http://localhost:8090/      # walk /setup → create the founding owner → a tenant + API key

# First request (Mock, or a BYOK provider credential added in the Console):
curl -H 'Authorization: Bearer gw_…' http://localhost:8080/v1/chat/completions \
  -d '{"model":"mock/test","messages":[{"role":"user","content":"hi"}]}'
```

**Config hot-reload (the acceptance check):** create or edit a route in the Console, then fire a request through the gateway and confirm the new routing takes effect **without restarting any pod** (allow a few seconds). This proves the gateway-server picked up the change from PostgreSQL. Propagation is by version-table polling (`dvara.config.poll-interval-ms`), which is **pooler-agnostic** — it holds no session-pinned connection, so it works over Cloud SQL private IP directly or behind a pooler in any mode.

## Workload Identity for secrets (hardened path)

`SECRET_MODE=k8s` (the default) creates the platform Secret directly — fastest to verify. For production, `SECRET_MODE=secret-manager` makes **GCP Secret Manager the source of truth** and projects the values into the `dvara-platform` k8s Secret via the Secret Manager CSI provider, using the pods' Workload-Identity-bound service account (`roles/secretmanager.secretAccessor`) — no secret ever rides a `helm --set` line or lands in Helm release metadata.

```bash
SECRET_MODE=secret-manager ./deploy.sh
```

This path needs the GKE Secret Manager CSI provider **and** a CSI volume mount on the pods so the `secretObjects` sync fires. The exact enablement (the managed `--enable-secret-manager` add-on vs the community `secrets-store-csi-driver` + GCP provider) varies by GKE version — **confirm for your cluster** before relying on it. The chart's provider-key `secretKeyRef`s are all `optional: true`, so the projected Secret only needs the five platform keys.

## Pinning the image tag

`deploy.sh` and `values-gke.yaml` default to a published GA tag (`1.2.1`). Never run `:latest` in production. Browse tags at the [GHCR package page](https://github.com/orgs/dvarahq/packages?repo_name=dvara); set `IMAGE_TAG` / `CHART_VERSION` to match.

## Related

- **[../README.md](../README.md)** — generic chart reference (single-tenant, multi-region)
- **[../../docker-compose/](../../docker-compose/)** — local stacks
- **[../../getting-started/](../../getting-started/)** — first-request scripts
