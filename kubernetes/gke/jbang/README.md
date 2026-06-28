# `DvaraGke` — jbang GKE provisioner

Structured, stateful, idempotent automation of the sibling [`../deploy.sh`](../deploy.sh) —
provisions a Dvara-on-GKE deployment **end to end**. Modeled on the DigitalOcean tool in
`dvara-infra/jbang/DvaraInfra.java`, but targets GCP by shelling out to `gcloud`/`kubectl`
(not the GCP Java SDK), so it mirrors `deploy.sh` command-for-command and stays testable.

> `deploy.sh` is the read-it-yourself reference; this is the repeatable, resumable runner.

## Prereqs
- [jbang](https://www.jbang.dev/) (`curl -Ls https://sh.jbang.dev | bash`), `gcloud` (authenticated, billing-enabled project), `kubectl`, `helm` 3.8+.
- A production-signed `DVARA_LICENSE_KEY`.

## Configure
```bash
cp values-gke-jbang.example.yaml gke.yaml
$EDITOR gke.yaml                       # fill REPLACE_ME (project, etc.)
# secrets come from the ENVIRONMENT, never the values file:
export DVARA_LICENSE_KEY='DVARA-...'
export DVARA_AUDIT_HMAC_SECRET=$(openssl rand -base64 32)
export DVARA_ENCRYPTION_MASTER_PASSWORD=$(openssl rand -base64 32)
export DVARA_ACTUATOR_API_KEY=$(openssl rand -base64 32)
export DVARA_ACTUATOR_METRICS_API_KEY=$(openssl rand -base64 32)
export DVARA_DB_PASSWORD=$(openssl rand -base64 24)
# optional: export OPENAI_API_KEY=… ANTHROPIC_API_KEY=…
```

## Run
```bash
./DvaraGke.java apply --values gke.yaml        # provision + install + DNS pause + smoke-test
# or phase by phase:
./DvaraGke.java provision --values gke.yaml    # APIs, VPC peering, GKE, Cloud SQL, (secret-manager) secrets+WI
./DvaraGke.java install   --values gke.yaml    # credentials, namespace, secret, helm upgrade --install
./DvaraGke.java smoke-test --values gke.yaml   # curl /actuator/health/readiness (TLS if domain set)
./DvaraGke.java output    --values gke.yaml    # print captured state
./DvaraGke.java update    --values gke.yaml [--restart]
./DvaraGke.java uninstall --values gke.yaml    # helm uninstall (infra kept)
./DvaraGke.java destroy   --values gke.yaml --yes   # delete cluster + Cloud SQL
```
Flags: `--state <path>` (default `<values>.state.json`), `--echo`, `--dry-run` (prints the
create/apply commands; read-backs still need real `gcloud`).

## How it maps to `deploy.sh`
`provision` = APIs → private-services VPC peering → VPC-native GKE + Workload Identity →
private-IP Cloud SQL → DB+user → (secret-manager mode) Secret Manager secrets + WI binding.
`install` = `get-credentials` → namespace → k8s Secret (or SecretProviderClass) → `helm
upgrade --install` with `values-gke.yaml` + the Cloud SQL private-IP DSN. Each step is
`describe → (exists? skip : create)`, so a re-run after a partial failure resumes.

## Tests
```bash
jbang DvaraGkeTest.java     # 11 unit tests — assert gcloud/helm command shape + idempotency (no GCP needed)
```

## Files
`DvaraGke.java` (CLI) · `Gcloud.java` (gcloud/kubectl wrapper) · `HelmGke.java` (helm/kubectl) ·
`GkeValues.java` (typed YAML) · `GkeState.java` (resumable state) · `Proc.java` (process abstraction,
the seam tests mock) · `DvaraGkeTest.java` · `values-gke-jbang.example.yaml`.

## Status
Builds + tests green. **Not yet run against a live cluster** — needs a real GCP project (the C6
verification dependency). This is the one-command executor for that verification once a project exists.
