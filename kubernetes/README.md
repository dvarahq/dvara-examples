# Kubernetes — Helm chart reference values

Two reference `values.yaml` files that consume the published DVARA Helm chart at `oci://ghcr.io/dvarahq/charts/dvara`.

| Shape | When to use |
|---|---|
| **[`single-tenant/`](single-tenant/values.yaml)** | "We run DVARA for ourselves on our k8s cluster, one tenant." Single-replica each, low resource footprint. ~5min install. |
| **[`multi-region/`](multi-region/values.yaml)** | "Multiple Kubernetes clusters, one per region (us-east-1 / eu-west-1 / ap-southeast-1)." One release per region, each with its own gateways and Flightdeck; the Flightdecks share one database. |

**Since 1.8.2 the gateway has no database.** Flightdeck is the only PostgreSQL client: put the
`SPRING_DATASOURCE_*` settings in `flightdeck.extraEnv`, never in `llmGatewayServer.extraEnv` (the
chart refuses that). The gateway enrols with Flightdeck inside the cluster using
`secrets.enrolmentSharedSecret`, which the chart generates on install and keeps across upgrades.
With `secrets.existingSecret`, add the key `enrolment-shared-secret` yourself (at least 32
characters). `llmGatewayServer.persistence.enabled: true` gives each gateway pod a volume for its
spool, config snapshot and certificate, and makes the gateway a StatefulSet.

## Pre-requisites (all shapes)

1. **Kubernetes 1.28+** (chart `kubeVersion: ">=1.28.0-0"`)
2. **Helm 3.8+** (OCI registry support — older Helm versions can't `helm pull` from OCI)
3. **PostgreSQL 16** instance reachable from Flightdeck. Not bundled by the chart; bring your own (managed cloud, self-hosted, etc.).
4. **A DVARA licence, optional.** Without one every feature runs, limited to 3 workspaces and 100,000 calls a month, for non-production use. A production-signed `DVARA-…` licence from your account team lifts the limits; a locally-minted test envelope will NOT validate.
5. **These secrets** at install time (generate each with `openssl rand -base64 32`):

| Secret | Purpose |
|---|---|
| `secrets.enterpriseLicenseKey` | Optional. The `DVARA-…` licence, copied into Flightdeck's licence store once, when it is empty. Renew it on the Licence page. |
| `secrets.gatewayEncryptionMasterPassword` | AES-256-GCM key for `ENCRYPTED`-mode provider credentials. **Loss is unrecoverable** — escrow offline (password manager + printed copy in a safe). |
| `secrets.auditHmacSecret` | Signs the audit chain. The same value on every process; refused on production profiles when blank or a placeholder. |
| `secrets.llmGatewayServerApiKey` | Bearer for `/actuator/gateway-status` + every authenticated `/actuator/*` path EXCEPT prometheus. Required on rc24+; chart still installs without it (boot WARNs) but the License Console will be dark. |
| `secrets.gatewayMetricsApiKey` | Bearer for `/actuator/prometheus` ONLY. **Must differ** from `llmGatewayServerApiKey` — principle of least privilege; a leaked metrics token does NOT unlock the license envelope. |

## Install

Chart is published as an OCI artifact:

```bash
# Verify (Helm 3.8+):
helm show chart oci://ghcr.io/dvarahq/charts/dvara --version 1.8.2

# Install (single-tenant example):
helm install dvara oci://ghcr.io/dvarahq/charts/dvara \
  --version 1.8.2 \
  --namespace dvara \
  --values ./single-tenant/values.yaml \
  --set "secrets.enterpriseLicenseKey=$DVARA_LICENSE_KEY" \
  --set "secrets.gatewayEncryptionMasterPassword=$DVARA_ENCRYPTION_MASTER_PASSWORD" \
  --set "secrets.auditHmacSecret=$DVARA_AUDIT_HMAC_SECRET" \
  --set "secrets.llmGatewayServerApiKey=$DVARA_ACTUATOR_API_KEY" \
  --set "secrets.gatewayMetricsApiKey=$DVARA_ACTUATOR_METRICS_API_KEY"
# The database URL is in single-tenant/values.yaml (flightdeck.extraEnv); its password is
# read from the Secret dvara-db, created beforehand in the dvara namespace.
```

For non-trivial deploys, manage secrets via an outer Secret resource + `secrets.create: false` + `secrets.existingSecret: <your-secret-name>` instead of inlining via `--set`.

## After install

```bash
# Verify pods are healthy:
kubectl get pods -n dvara
kubectl logs -n dvara -l app.kubernetes.io/component=gateway-server -f

# Port-forward the Console (in the multi-region shape, each region has its own):
kubectl port-forward -n dvara svc/dvara-flightdeck 8090:8090
```

Open `http://localhost:8090/`:
1. Walk `/setup` to create the founding platform owner
2. Console → Workspaces → New workspace
3. Portal → API Keys → New key (plaintext shown once; copy it)
4. Portal → Credentials → Add credential (BYOK — paste the upstream provider key; AES-256-GCM at rest)
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

- **[doks/](doks/)** — DigitalOcean Kubernetes (DOKS). On DigitalOcean, use DOKS or a Droplet running a Compose stack; App Platform is not supported.
- **[../docker-compose/](../docker-compose/)** — local Docker Compose stacks
- **[../getting-started/](../getting-started/)** — first-request scripts after the deploy is up
- **[../sdk-integrations/](../sdk-integrations/)** — OpenAI SDK / LangChain / LiteLLM / Spring AI examples
