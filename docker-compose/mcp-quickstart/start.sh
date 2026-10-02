#!/usr/bin/env bash
# DVARA MCP quickstart: one command from nothing to a governed MCP tool call.
#
#   ./start.sh      writes .env, starts the demo MCP server, the stack, and seeds it
#   ./demo.sh       sends four tool calls through the gateway and shows what happened
#   ./stop.sh       stops everything (add --reset to delete the data too)
#
# Needs Docker with Compose v2, JBang (https://www.jbang.dev) and curl.
set -euo pipefail
cd "$(dirname "$0")"

FLIGHTDECK=http://localhost:8090
GATEWAY=http://localhost:8080
MCP_PORT=8060
# The MCP server's id in seed-config.json; the catalog sync addresses it by this.
MCP_SERVER_ID=6d0f3a52-9a1e-4c4e-9a37-3f5c2d1b7a01
STATE=.quickstart
mkdir -p "$STATE"

say()  { printf '\033[1;36m▸ %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

for cmd in docker jbang curl openssl; do
  command -v "$cmd" >/dev/null || fail "$cmd is not installed. See README.md for prerequisites."
done
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."

# --- 1. Secrets ----------------------------------------------------------------------
# Generated once. Delete .env (and run ./stop.sh --reset) to start again from scratch.
if [[ ! -f .env ]]; then
  say "Generating .env"
  secret() { openssl rand -base64 32 | tr -d '\n'; }
  cat > .env <<EOF
# Written by start.sh. Local evaluation only: no licence key, no provider key.
DVARA_LICENSE_KEY=
DVARA_ENROLMENT_SECRET=$(secret)
DVARA_ENCRYPTION_MASTER_PASSWORD=$(secret)
DVARA_ACTUATOR_API_KEY=$(secret)
DVARA_ACTUATOR_METRICS_API_KEY=$(secret)
DVARA_AUDIT_HMAC_SECRET=$(secret)
DVARA_PII_TOKEN_ENCRYPTION_MASTER_PASSWORD=$(secret)
DB_PASSWORD=dvara
# The workspace API key, seeded by bootstrap.yaml. Send it as Authorization: Bearer.
DEMO_API_KEY=gw_$(openssl rand -hex 24)
# The Flightdeck owner start.sh creates. Sign in at $FLIGHTDECK with these.
OWNER_EMAIL=owner@quickstart.local
OWNER_PASSWORD=$(openssl rand -hex 12)
EOF
fi
set -a; source .env; set +a

# --- 2. The demo MCP server -------------------------------------------------------------
if curl -s -o /dev/null "http://localhost:$MCP_PORT/mcp" -X POST; then
  say "Demo MCP server already running on :$MCP_PORT"
else
  say "Starting the demo MCP server with JBang (the first run downloads Spring AI)"
  nohup jbang DemoMcpServer.java > "$STATE/mcp-server.log" 2>&1 &
  for _ in $(seq 1 120); do
    grep -q 'Started DemoMcpServer' "$STATE/mcp-server.log" 2>/dev/null && break
    grep -q 'APPLICATION FAILED' "$STATE/mcp-server.log" 2>/dev/null && fail "MCP server failed; see $STATE/mcp-server.log"
    sleep 2
  done
  grep -q 'Started DemoMcpServer' "$STATE/mcp-server.log" || fail "MCP server did not start; see $STATE/mcp-server.log"
fi

# --- 3. The stack ------------------------------------------------------------------------
say "Starting PostgreSQL, Flightdeck and the gateway (the first pull takes a few minutes)"
docker compose up -d --quiet-pull

wait_for() { # url, label, tries
  for _ in $(seq 1 "${3:-90}"); do
    curl -sf -o /dev/null "$1" && return 0
    sleep 3
  done
  fail "$2 did not come up. Check: docker compose logs"
}
wait_for "$FLIGHTDECK/actuator/health/liveness" "Flightdeck"
wait_for "$GATEWAY/actuator/health/liveness" "The gateway"

# --- 4. Seed Flightdeck ------------------------------------------------------------------
# bootstrap.yaml has already created the workspace and the API key. Everything else goes
# in through the Console's own endpoints, as a signed-in owner would: create the owner,
# sign in, then import seed-config.json (PII redaction, the MCP server, the deny policy).
JAR="$STATE/cookies"
rm -f "$JAR"
fd()   { curl -s -b "$JAR" -c "$JAR" "$@"; }
csrf() { grep -oE 'name="csrf-token" content="[^"]+"' | head -1 | cut -d'"' -f4; }

if [[ $(fd -o /dev/null -w '%{http_code}' "$FLIGHTDECK/setup") == 200 ]]; then
  say "Creating the Flightdeck owner ($OWNER_EMAIL)"
  token=$(fd "$FLIGHTDECK/setup" | csrf)
  fd -o /dev/null "$FLIGHTDECK/setup" --data-urlencode "_csrf=$token" \
    --data-urlencode first_name=Quickstart --data-urlencode last_name=Owner \
    --data-urlencode "email=$OWNER_EMAIL" --data-urlencode "password=$OWNER_PASSWORD"
fi

token=$(fd "$FLIGHTDECK/login" | csrf)
landing=$(fd -o /dev/null -w '%{redirect_url}' "$FLIGHTDECK/login" --data-urlencode "_csrf=$token" \
  --data-urlencode "email=$OWNER_EMAIL" --data-urlencode "password=$OWNER_PASSWORD")
[[ $landing == *"/login"* ]] && fail "Could not sign in to Flightdeck as $OWNER_EMAIL. Run ./stop.sh --reset and start again."

say "Importing the MCP server, the deny policy and PII redaction"
token=$(fd "$FLIGHTDECK/config/import" | csrf)
fd "$FLIGHTDECK/config/import/preview" --data-urlencode "_csrf=$token" --data-urlencode mode=merge \
  --data-urlencode "payload@seed-config.json" > "$STATE/preview.html"
hash=$(grep -oE 'name="contentHash"[^>]*value="[^"]+"|value="[^"]+"[^>]*name="contentHash"' "$STATE/preview.html" \
  | grep -oE 'value="[^"]+"' | head -1 | cut -d'"' -f2)
[[ -n $hash ]] || fail "Flightdeck rejected seed-config.json; open $STATE/preview.html to see why."
token=$(csrf < "$STATE/preview.html")
fd -o /dev/null "$FLIGHTDECK/config/import/apply" --data-urlencode "_csrf=$token" --data-urlencode "contentHash=$hash"

say "Reading the demo server's tools into the catalog"
token=$(fd "$FLIGHTDECK/mcp/servers" | csrf)
fd -o /dev/null -X POST -H "X-CSRF-TOKEN: $token" "$FLIGHTDECK/mcp/servers/$MCP_SERVER_ID/sync"

# The gateway reads its configuration from Flightdeck every few seconds.
say "Waiting for the gateway to pick up the configuration"
for _ in $(seq 1 30); do
  tools=$(curl -s "$GATEWAY/mcp" -H "Authorization: Bearer $DEMO_API_KEY" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | grep -o 'demo-tools__' | wc -l || true)
  (( tools > 0 )) && break
  sleep 2
done
(( tools > 0 )) || fail "The gateway does not list the demo tools yet. Check: docker compose logs dvara-gateway"

cat <<EOF

$(printf '\033[1;32m✓ Ready.\033[0m') DVARA is governing the demo MCP server.

  MCP endpoint   $GATEWAY/mcp   (Streamable HTTP)
  API key        $DEMO_API_KEY
  Flightdeck     $FLIGHTDECK   sign in as $OWNER_EMAIL / $OWNER_PASSWORD

  Next: ./demo.sh   sends four tool calls and shows what the gateway did with each.
EOF
