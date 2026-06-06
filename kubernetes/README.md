# Kubernetes — Helm chart reference values

Three reference `values.yaml` files that consume the published DVARA Helm chart at `oci://ghcr.io/dvarahq/dvara/charts/meridian`. **The chart itself lives in [`dvarahq/dvara`](https://github.com/dvarahq/dvara/tree/main/charts/meridian) — these are deploy recipes that point at it.**

| Shape | When to use |
|---|---|
| **[`single-tenant/`](single-tenant/values.yaml)** | "We run DVARA for ourselves on our k8s cluster, one tenant." Single-replica each, low resource footprint. ~5min install. |
| **[`multi-tenant-saas/`](multi-tenant-saas/values.yaml)** | "We operate DVARA AS A SaaS — each customer becomes a tenant." Includes flightdeck Console, MCP Proxy, autoscaling, PDB, ServiceMonitor. |
| **[`multi-region/`](multi-region/values.yaml)** | "Multiple Kubernetes clusters, one per region (us-east-1 / eu-west-1 / ap-southeast-1)." Region-local gateway + MCP; centralized flightdeck in one region. Per-region tenant data residency. |

## Pre-requisites (all shapes)

1. **Kubernetes 1.28+** (chart `kubeVersion: ">=1.28.0-0"`)
2. **Helm 3.8+** (OCI registry support — older Helm versions can't `helm pull` from OCI)
3. **PostgreSQL 16** instance reachable from the cluster. Not bundled by the chart; bring your own (managed cloud, self-hosted, etc.). For SaaS shape, size for your tenant count.
4. **DVARA license envelope** — production-signed `DVARA-…` string. Request from your DVARA account team. The GHCR images carry only the production verify key; a locally-minted test envelope will NOT validate.
5. **Four secrets** at install time (three of these via `openssl rand -base64 32`):

| Secret | Purpose |
|---|---|
| `secrets.enterpriseLicenseKey` | The `DVARA-…` envelope. Validated at boot. |
| `secrets.gatewayEncryptionMasterPassword` | AES-256-GCM key for `ENCRYPTED`-mode provider credentials. **Loss is unrecoverable** — escrow offline (password manager + printed copy in a safe). |
| `secrets.gatewayServerApiKey` | Bearer for `/actuator/gateway-status` + every authenticated `/actuator/*` path EXCEPT prometheus. Required on rc24+; chart still installs without it (boot WARNs) but the License Console will be dark. |
| `secrets.gatewayMetricsApiKey` | Bearer for `/actuator/prometheus` ONLY. **Must differ** from `gatewayServerApiKey` — principle of least privilege; a leaked metrics token does NOT unlock the license envelope. |

## Install

Chart is published as an OCI artifact:

```bash
# Verify (Helm 3.8+):
helm show chart oci://ghcr.io/dvarahq/dvara/charts/meridian --version 1.0.0

# Install (single-tenant example):
helm install dvara oci://ghcr.io/dvarahq/dvara/charts/meridian \
  --version 1.0.0 \
  --namespace dvara --create-namespace \
  --values ./single-tenant/values.yaml \
  --set "secrets.enterpriseLicenseKey=$DVARA_LICENSE_KEY" \
  --set "secrets.gatewayEncryptionMasterPassword=$DVARA_ENCRYPTION_MASTER_PASSWORD" \
  --set "secrets.gatewayServerApiKey=$DVARA_ACTUATOR_API_KEY" \
  --set "secrets.gatewayMetricsApiKey=$DVARA_ACTUATOR_METRICS_API_KEY" \
  --set "gatewayServer.extraEnv[0].name=SPRING_DATASOURCE_URL,gatewayServer.extraEnv[0].value=$DVARA_DB_URL"
```

For non-trivial deploys, manage secrets via an outer Secret resource + `secrets.create: false` + `secrets.existingSecret: <your-secret-name>` instead of inlining via `--set`.

## After install

```bash
# Verify pods are healthy:
kubectl get pods -n dvara
kubectl logs -n dvara -l app.kubernetes.io/component=gateway-server -f

# Port-forward the Console (single-tenant + multi-tenant-saas shapes;
# multi-region centralizes flightdeck in one region):
kubectl port-forward -n dvara svc/dvara-meridian-flightdeck 8090:8090
```

Open `http://localhost:8090/`:
1. Walk `/setup` to create the founding platform owner
2. Console → Tenants → New tenant
3. Tenant Portal → API Keys → New key (plaintext shown once; copy it)
4. Tenant Portal → Credentials → Add credential (BYOK — paste the upstream provider key; AES-256-GCM at rest)
5. `curl -H "Authorization: Bearer gw_…" http://<gateway-server-host>:8080/v1/chat/completions -d '{"model":"…","messages":[…]}'`

## Probes — why no `/status`

The chart uses `/actuator/health/{readiness,liveness}` for probes. The legacy `/status` endpoint was **deleted in rc26** (replaced by `/actuator/gateway-status` under Bearer auth). If you see a values file or external example referencing `/status`, it's stale — every reference in this directory uses the post-rc26 anonymous probe paths.

## Probes — why `optional: true` on the Bearer env vars

The chart marks `DVARA_ACTUATOR_API_KEY` + `DVARA_ACTUATOR_METRICS_API_KEY` as `optional: true` in the deployment templates so the chart still installs without them set. Without them:

- `/actuator/gateway-status` returns 401 on every call → flightdeck's connection pill stays red, License Console doesn't work
- `/actuator/prometheus` returns 401 on every scrape → no metrics
- Anonymous probes (`/health`, `/health/{readiness,liveness}`, `/info`) still work, so the pod looks fine — **silent actuator failure**

Set them at install time on every production deploy. The boot log emits a WARN listing the missing variables if either is unset.

## Helm test

After install, run `helm test dvara -n dvara` to fire the bundled smoke test against `/actuator/health` on the gateway-server service and `/` on the flightdeck service. Both should pass within seconds.

## Related

- **[../digitalocean/](../digitalocean/)** — DigitalOcean App Platform one-click deploys (smoke + production)
- **[../docker-compose/](../docker-compose/)** — local Docker Compose stacks
- **[../getting-started/](../getting-started/)** — first-request scripts after the deploy is up
- **[../sdk-integrations/](../sdk-integrations/)** — OpenAI SDK / LangChain / LiteLLM / Spring AI examples
- **Chart source** — [`dvarahq/dvara/charts/meridian/`](https://github.com/dvarahq/dvara/tree/main/charts/meridian)
