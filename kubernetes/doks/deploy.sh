#!/usr/bin/env bash
#
# DVARA AI Gateway → DigitalOcean Kubernetes (DOKS), end to end.
#
# Provisions a DOKS cluster + a DO Managed PostgreSQL database, installs
# Traefik v3 (ingress + Let's Encrypt), materializes the platform secrets as
# a k8s Secret, and `helm install`s the cloud-agnostic dvara chart with the
# DOKS overlay (values-doks.yaml). Idempotent-ish: steps skip if the
# resource already exists.
#
# REFERENCE script — read it, don't trust it blind. It runs real `doctl`
# billable operations. The Traefik Helm value paths vary by chart version —
# confirm them for the version you install.
#
# Prereqs: doctl (authenticated: `doctl auth init`), kubectl, helm 3.8+, openssl.
#          A production-signed DVARA_LICENSE_KEY. A domain for Let's Encrypt.
#
# Usage:
#   export DVARA_LICENSE_KEY='DVARA-...'
#   export BASE_DOMAIN=dvara.example.com
#   export ACME_EMAIL=ops@example.com
#   ./deploy.sh
#
set -euo pipefail

# ----------------------------------------------------------------------------
# Config — override via env before invoking.
# ----------------------------------------------------------------------------
REGION="${REGION:-nyc1}"
CLUSTER="${CLUSTER:-dvara}"
NODE_SIZE="${NODE_SIZE:-s-2vcpu-4gb}"
NODE_COUNT="${NODE_COUNT:-2}"
K8S_VERSION="${K8S_VERSION:-}"                   # empty → latest; or e.g. 1.32.x-do.0 (see `doctl k8s options versions`)
NS="${NS:-dvara}"
RELEASE="${RELEASE:-dvara}"
CHART="${CHART:-oci://ghcr.io/dvarahq/charts/dvara}"
CHART_VERSION="${CHART_VERSION:-1.2.1}"          # pin to the published GA tag; use 1.2.1 once cut
IMAGE_TAG="${IMAGE_TAG:-1.2.1}"                   # never :latest in production
DB_NAME="${DB_NAME:-dvara-pg}"
DB_SIZE="${DB_SIZE:-db-s-1vcpu-2gb}"
DB_VERSION="${DB_VERSION:-16}"
BASE_DOMAIN="${BASE_DOMAIN:?set BASE_DOMAIN (e.g. dvara.example.com) for Let's Encrypt}"
ACME_EMAIL="${ACME_EMAIL:?set ACME_EMAIL for Let's Encrypt registration}"
DVARA_LICENSE_KEY="${DVARA_LICENSE_KEY:?set DVARA_LICENSE_KEY to a signed DVARA- envelope}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
say() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }

# ----------------------------------------------------------------------------
# 1. DOKS cluster
# ----------------------------------------------------------------------------
say "DOKS cluster ${CLUSTER} (${REGION})"
if ! doctl kubernetes cluster get "$CLUSTER" >/dev/null 2>&1; then
  VER_FLAG=()
  [ -n "$K8S_VERSION" ] && VER_FLAG=(--version "$K8S_VERSION")
  doctl kubernetes cluster create "$CLUSTER" \
    --region "$REGION" \
    --node-pool "name=default;size=${NODE_SIZE};count=${NODE_COUNT}" \
    "${VER_FLAG[@]}" --wait
fi
doctl kubernetes cluster kubeconfig save "$CLUSTER"
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
CLUSTER_ID="$(doctl kubernetes cluster get "$CLUSTER" --format ID --no-header)"

# ----------------------------------------------------------------------------
# 2. DO Managed PostgreSQL (private networking) + database + user
# ----------------------------------------------------------------------------
say "DO Managed PostgreSQL ${DB_NAME}"
if ! doctl databases list --format Name --no-header | grep -qx "$DB_NAME"; then
  doctl databases create "$DB_NAME" \
    --engine pg --version "$DB_VERSION" \
    --region "$REGION" --size "$DB_SIZE" --num-nodes 1 --wait
fi
DB_ID="$(doctl databases list --format ID,Name --no-header | awk -v n="$DB_NAME" '$2==n{print $1}')"

# Lock the DB down to the cluster only (trusted source), then app db + user.
doctl databases firewalls append "$DB_ID" --rule "k8s:${CLUSTER_ID}" 2>/dev/null || true
doctl databases db create   "$DB_ID" dvara 2>/dev/null || true
doctl databases user create "$DB_ID" dvara 2>/dev/null || true

# Resolve the PRIVATE connection details (port 25060, sslmode=require).
DB_PRIVATE_HOST="$(doctl databases connection "$DB_ID" --private --format Host --no-header)"
DB_PASS="$(doctl databases user get "$DB_ID" dvara --format Password --no-header)"
say "DB private host: ${DB_PRIVATE_HOST}:25060 (sslmode=require, direct connection)"

# ----------------------------------------------------------------------------
# 3. Traefik v3 — ingress + Let's Encrypt (ingress-nginx is EOL since Mar 2026)
# ----------------------------------------------------------------------------
say "Traefik v3 (ingress + Let's Encrypt)"
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo update >/dev/null
# Value paths are Traefik-chart-version-sensitive — confirm against the chart
# version you pull (`helm show values traefik/traefik`).
helm upgrade --install traefik traefik/traefik \
  --namespace traefik --create-namespace \
  --set "certificatesResolvers.letsencrypt.acme.email=${ACME_EMAIL}" \
  --set "certificatesResolvers.letsencrypt.acme.storage=/data/acme.json" \
  --set "certificatesResolvers.letsencrypt.acme.tlsChallenge=true" \
  --set "persistence.enabled=true" \
  --wait

# ----------------------------------------------------------------------------
# 4. Secrets — DB password + the 5 platform keys (plain k8s Secret; DO has no
#    Workload Identity / Secret Manager). Provider keys are BYOK via Console.
# ----------------------------------------------------------------------------
say "Secrets"
ENC="$(openssl rand -base64 32)"
ACT="$(openssl rand -base64 32)"
MET="$(openssl rand -base64 32)"
HMAC="$(openssl rand -base64 32)"
[ "$ACT" != "$MET" ] || { echo "actuator keys collided — rerun"; exit 1; }

kubectl -n "$NS" create secret generic dvara-db \
  --from-literal=password="$DB_PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NS" create secret generic dvara-platform \
  --from-literal=enterprise-license-key="$DVARA_LICENSE_KEY" \
  --from-literal=gateway-encryption-master-password="$ENC" \
  --from-literal=gateway-server-api-key="$ACT" \
  --from-literal=gateway-metrics-api-key="$MET" \
  --from-literal=audit-hmac-secret="$HMAC" \
  --dry-run=client -o yaml | kubectl apply -f -

# ----------------------------------------------------------------------------
# 5. Render values + helm install
# ----------------------------------------------------------------------------
say "helm install ${RELEASE}"
RENDERED="$(mktemp)"
sed "s/@@DB_PRIVATE_HOST@@/${DB_PRIVATE_HOST}/g;
     s/@@BASE_DOMAIN@@/${BASE_DOMAIN}/g" "$HERE/values-doks.yaml" > "$RENDERED"

helm upgrade --install "$RELEASE" "$CHART" \
  --version "$CHART_VERSION" \
  --namespace "$NS" --create-namespace \
  --values "$RENDERED" \
  --set "gatewayServer.image.tag=${IMAGE_TAG}" \
  --set "flightdeck.image.tag=${IMAGE_TAG}" \
  --wait --timeout 10m

# ----------------------------------------------------------------------------
# 6. Verify
# ----------------------------------------------------------------------------
say "Rollout + health"
kubectl -n "$NS" rollout status deploy -l app.kubernetes.io/instance="$RELEASE" --timeout=5m
kubectl -n "$NS" get pods
LB_IP="$(kubectl -n traefik get svc traefik -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"

cat <<EOF

✅ Installed. DO Load Balancer IP: ${LB_IP:-<pending — re-check: kubectl -n traefik get svc traefik>}
   Point A-records: api.${BASE_DOMAIN} → ${LB_IP:-LB_IP} and flightdeck.${BASE_DOMAIN} → ${LB_IP:-LB_IP}
   Traefik issues Let's Encrypt certs once DNS resolves (a few minutes).

Next:
  kubectl -n ${NS} port-forward svc/${RELEASE}-flightdeck 8090:8090
  open http://localhost:8090/    # walk /setup → create tenant + API key
  # config hot-reload: create a route in the Console, fire a request through
  # the gateway, confirm it routes WITHOUT a pod restart (config-version poll —
  # pooler-agnostic; works over the direct :25060 connection or any pool, within
  # a few seconds).
EOF
