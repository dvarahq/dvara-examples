#!/usr/bin/env bash
#
# DVARA AI Gateway → Google Kubernetes Engine (GKE), end to end.
#
# Provisions a VPC-native GKE cluster + a private-IP Cloud SQL Postgres
# instance, wires Workload Identity, materializes the platform secrets, and
# `helm install`s the cloud-agnostic dvara chart with the GKE overlay
# (values-gke.yaml). Idempotent-ish: each step skips if the resource
# already exists, so a re-run after a partial failure resumes.
#
# This is a REFERENCE script — read it, don't trust it blind. It runs real
# `gcloud` billable operations. Verify each command against YOUR GKE / Cloud
# SQL version; GCP CLI surfaces drift between releases (especially the
# Secret Manager add-on flag in SECRET_MODE=secret-manager).
#
# Prereqs: gcloud (authenticated, billing-enabled project), kubectl, helm 3.8+,
#          openssl. A production-signed DVARA_LICENSE_KEY.
#
# Usage:
#   export PROJECT_ID=my-gcp-project
#   export DVARA_LICENSE_KEY='DVARA-...'        # from your DVARA account team
#   export BASE_DOMAIN=dvara.example.com        # omit for the IP-only smoke
#   ./deploy.sh
#
set -euo pipefail

# ----------------------------------------------------------------------------
# Config — override any of these via env before invoking.
# ----------------------------------------------------------------------------
PROJECT_ID="${PROJECT_ID:?set PROJECT_ID to your GCP project}"
REGION="${REGION:-us-central1}"
CLUSTER="${CLUSTER:-dvara}"
NETWORK="${NETWORK:-default}"
SUBNET="${SUBNET:-default}"
NS="${NS:-dvara}"
RELEASE="${RELEASE:-dvara}"
CHART="${CHART:-oci://ghcr.io/dvarahq/charts/dvara}"
CHART_VERSION="${CHART_VERSION:-1.0.1}"          # pin to the published GA tag; use 1.1.0 once cut
IMAGE_TAG="${IMAGE_TAG:-1.0.1}"                   # never :latest in production
SQL_INSTANCE="${SQL_INSTANCE:-dvara-pg}"
SQL_TIER="${SQL_TIER:-db-custom-1-3840}"
GSA="${GSA:-dvara-gke}"                           # GCP service account for Workload Identity
STATIC_IP_NAME="${STATIC_IP_NAME:-dvara-ip}"
SECRET_MODE="${SECRET_MODE:-k8s}"                 # k8s | secret-manager
BASE_DOMAIN="${BASE_DOMAIN:-}"                    # empty → IP-only smoke (no managed cert)
DVARA_LICENSE_KEY="${DVARA_LICENSE_KEY:?set DVARA_LICENSE_KEY to a signed DVARA- envelope}"

GSA_EMAIL="${GSA}@${PROJECT_ID}.iam.gserviceaccount.com"
API_HOST="api.${BASE_DOMAIN}"
FLIGHTDECK_HOST="flightdeck.${BASE_DOMAIN}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }

say "Project ${PROJECT_ID} / region ${REGION} / secret-mode ${SECRET_MODE}"
gcloud config set project "$PROJECT_ID" >/dev/null

# ----------------------------------------------------------------------------
# 1. Enable APIs
# ----------------------------------------------------------------------------
say "Enabling APIs"
gcloud services enable \
  container.googleapis.com sqladmin.googleapis.com \
  servicenetworking.googleapis.com secretmanager.googleapis.com \
  compute.googleapis.com

# ----------------------------------------------------------------------------
# 2. Private services access — required so Cloud SQL gets a PRIVATE IP that a
#    VPC-native cluster reaches directly (the "direct connection" that keeps
#    PG NOTIFY/LISTEN config hot-reload working).
# ----------------------------------------------------------------------------
say "Private services access for Cloud SQL"
if ! gcloud compute addresses describe google-managed-services-"$NETWORK" --global >/dev/null 2>&1; then
  gcloud compute addresses create google-managed-services-"$NETWORK" \
    --global --purpose=VPC_PEERING --prefix-length=16 --network="$NETWORK"
fi
gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-"$NETWORK" --network="$NETWORK" 2>/dev/null || true

# ----------------------------------------------------------------------------
# 3. VPC-native GKE cluster with Workload Identity
# ----------------------------------------------------------------------------
say "GKE cluster ${CLUSTER}"
if ! gcloud container clusters describe "$CLUSTER" --region "$REGION" >/dev/null 2>&1; then
  EXTRA=()
  [ "$SECRET_MODE" = "secret-manager" ] && EXTRA+=(--enable-secret-manager)  # CONFIRM flag for your GKE version
  gcloud container clusters create "$CLUSTER" \
    --region "$REGION" --num-nodes 1 \
    --release-channel regular \
    --enable-ip-alias \
    --workload-pool="${PROJECT_ID}.svc.id.goog" \
    --network "$NETWORK" --subnetwork "$SUBNET" \
    "${EXTRA[@]}"
fi
gcloud container clusters get-credentials "$CLUSTER" --region "$REGION"
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

# ----------------------------------------------------------------------------
# 4. Cloud SQL Postgres (private IP only) + database + user
# ----------------------------------------------------------------------------
say "Cloud SQL ${SQL_INSTANCE}"
DB_PASS="${DB_PASS:-$(openssl rand -base64 24)}"
if ! gcloud sql instances describe "$SQL_INSTANCE" >/dev/null 2>&1; then
  gcloud sql instances create "$SQL_INSTANCE" \
    --database-version=POSTGRES_16 --tier="$SQL_TIER" --region="$REGION" \
    --network="projects/${PROJECT_ID}/global/networks/${NETWORK}" --no-assign-ip
fi
gcloud sql databases create dvara --instance="$SQL_INSTANCE" 2>/dev/null || true
gcloud sql users create dvara --instance="$SQL_INSTANCE" --password="$DB_PASS" 2>/dev/null \
  || gcloud sql users set-password dvara --instance="$SQL_INSTANCE" --password="$DB_PASS"
CLOUDSQL_PRIVATE_IP="$(gcloud sql instances describe "$SQL_INSTANCE" \
  --format='value(ipAddresses.filter("type=PRIVATE").extract("ipAddress").flatten())')"
say "Cloud SQL private IP: ${CLOUDSQL_PRIVATE_IP}"

# ----------------------------------------------------------------------------
# 5. Workload Identity — GCP SA bound to the two chart KSAs
#    (dvara-gateway-server, dvara-flightdeck, pinned in values-gke.yaml).
# ----------------------------------------------------------------------------
say "Workload Identity service account ${GSA_EMAIL}"
gcloud iam service-accounts create "$GSA" 2>/dev/null || true
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${GSA_EMAIL}" --role="roles/cloudsql.client" >/dev/null
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${GSA_EMAIL}" --role="roles/secretmanager.secretAccessor" >/dev/null
for KSA in dvara-gateway-server dvara-flightdeck; do
  gcloud iam service-accounts add-iam-policy-binding "$GSA_EMAIL" \
    --role roles/iam.workloadIdentityUser \
    --member "serviceAccount:${PROJECT_ID}.svc.id.goog[${NS}/${KSA}]" >/dev/null
done

# ----------------------------------------------------------------------------
# 6. Platform secrets (license / encryption password / 2 actuator Bearers /
#    audit HMAC) + DB password. The chart's provider-key refs are optional:true,
#    so the platform Secret carries only these five keys.
# ----------------------------------------------------------------------------
say "Platform secrets (mode: ${SECRET_MODE})"
ENC="$(openssl rand -base64 32)"
ACT="$(openssl rand -base64 32)"
MET="$(openssl rand -base64 32)"
HMAC="$(openssl rand -base64 32)"
[ "$ACT" != "$MET" ] || { echo "actuator keys collided — rerun"; exit 1; }

# DB password Secret (referenced by values-gke.yaml secretKeyRef name=dvara-db).
kubectl -n "$NS" create secret generic dvara-db \
  --from-literal=password="$DB_PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

if [ "$SECRET_MODE" = "secret-manager" ]; then
  # Workload-Identity-for-secrets: source of truth = Secret Manager; the CSI
  # SecretProviderClass projects them into the `dvara-platform` k8s Secret.
  declare -A SM=(
    [dvara-license-key]="$DVARA_LICENSE_KEY"
    [dvara-encryption-master-password]="$ENC"
    [dvara-actuator-api-key]="$ACT"
    [dvara-actuator-metrics-api-key]="$MET"
    [dvara-audit-hmac-secret]="$HMAC"
  )
  for name in "${!SM[@]}"; do
    gcloud secrets describe "$name" >/dev/null 2>&1 || gcloud secrets create "$name" --replication-policy=automatic
    printf '%s' "${SM[$name]}" | gcloud secrets versions add "$name" --data-file=-
  done
  sed "s/@@PROJECT_ID@@/${PROJECT_ID}/g; s/@@API_HOST@@/${API_HOST}/g; s/@@FLIGHTDECK_HOST@@/${FLIGHTDECK_HOST}/g" \
    "$HERE/secretproviderclass.yaml" | kubectl -n "$NS" apply -f -
  echo "NOTE: secret-manager mode needs the GKE Secret Manager CSI provider + a"
  echo "      CSI volume mount on the pods so secretObjects sync. Confirm the"
  echo "      add-on for your GKE version (README §Workload Identity for secrets)."
else
  # k8s mode — fastest first-boot verification. Five chart keys, created direct.
  kubectl -n "$NS" create secret generic dvara-platform \
    --from-literal=enterprise-license-key="$DVARA_LICENSE_KEY" \
    --from-literal=gateway-encryption-master-password="$ENC" \
    --from-literal=gateway-server-api-key="$ACT" \
    --from-literal=gateway-metrics-api-key="$MET" \
    --from-literal=audit-hmac-secret="$HMAC" \
    --dry-run=client -o yaml | kubectl apply -f -
fi

# ----------------------------------------------------------------------------
# 7. Reserve a global static IP for the GCE Ingress
# ----------------------------------------------------------------------------
say "Static IP ${STATIC_IP_NAME}"
gcloud compute addresses describe "$STATIC_IP_NAME" --global >/dev/null 2>&1 \
  || gcloud compute addresses create "$STATIC_IP_NAME" --global
INGRESS_IP="$(gcloud compute addresses describe "$STATIC_IP_NAME" --global --format='value(address)')"

# ----------------------------------------------------------------------------
# 8. Render values + helm install
# ----------------------------------------------------------------------------
say "helm install ${RELEASE}"
RENDERED="$(mktemp)"
sed "s/@@PROJECT_ID@@/${PROJECT_ID}/g;
     s/@@CLOUDSQL_PRIVATE_IP@@/${CLOUDSQL_PRIVATE_IP}/g;
     s/@@STATIC_IP_NAME@@/${STATIC_IP_NAME}/g;
     s/@@API_HOST@@/${API_HOST}/g;
     s/@@FLIGHTDECK_HOST@@/${FLIGHTDECK_HOST}/g" "$HERE/values-gke.yaml" > "$RENDERED"

HELM_EXTRA=()
HELM_EXTRA+=(--set "gatewayServer.image.tag=${IMAGE_TAG}")
HELM_EXTRA+=(--set "flightdeck.image.tag=${IMAGE_TAG}")
if [ -z "$BASE_DOMAIN" ]; then
  # IP-only smoke: no managed cert, allow HTTP, host = the static IP.
  HELM_EXTRA+=(--set "ingress.annotations.networking\.gke\.io/managed-certificates=null")
  HELM_EXTRA+=(--set "ingress.annotations.kubernetes\.io/ingress\.allow-http=true")
  HELM_EXTRA+=(--set "ingress.gatewayServer.hosts[0].host=${INGRESS_IP}")
  echo "IP-only smoke — Console will be reachable at http://${INGRESS_IP}/ once the LB is up."
fi

helm upgrade --install "$RELEASE" "$CHART" \
  --version "$CHART_VERSION" \
  --namespace "$NS" --create-namespace \
  --values "$RENDERED" \
  "${HELM_EXTRA[@]}" \
  --wait --timeout 10m

# Managed cert only when a domain is set.
if [ -n "$BASE_DOMAIN" ]; then
  sed "s/@@API_HOST@@/${API_HOST}/g; s/@@FLIGHTDECK_HOST@@/${FLIGHTDECK_HOST}/g" \
    "$HERE/managed-certificate.yaml" | kubectl -n "$NS" apply -f -
fi

# ----------------------------------------------------------------------------
# 9. Verify
# ----------------------------------------------------------------------------
say "Rollout + health"
kubectl -n "$NS" rollout status deploy -l app.kubernetes.io/instance="$RELEASE" --timeout=5m
kubectl -n "$NS" get pods

cat <<EOF

✅ Installed. Ingress IP: ${INGRESS_IP}
   ${BASE_DOMAIN:+Point A-records: ${API_HOST} → ${INGRESS_IP} and ${FLIGHTDECK_HOST} → ${INGRESS_IP}}
   ${BASE_DOMAIN:+Managed cert provisions 15-60 min after DNS resolves:}
   ${BASE_DOMAIN:+  kubectl -n ${NS} describe managedcertificate dvara-cert}

Next (see README "Verify"):
  kubectl -n ${NS} port-forward svc/${RELEASE}-flightdeck 8090:8090
  open http://localhost:8090/    # walk /setup → create tenant + API key
  # config hot-reload: create a route in the Console, fire a request through
  # the gateway, confirm it routes WITHOUT a pod restart (PG NOTIFY/LISTEN).
EOF
