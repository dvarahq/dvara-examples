#!/usr/bin/env bash
# Sends four MCP requests through the DVARA gateway, as an agent would, and prints what
# came back. Run ./start.sh first.
set -euo pipefail
cd "$(dirname "$0")"
[[ -f .env ]] || { echo "Run ./start.sh first." >&2; exit 1; }
set -a; source .env; set +a

GATEWAY=http://localhost:8080
FLIGHTDECK=http://localhost:8090

mcp() { # method, params-json
  local params=${2:-'{}'}
  curl -s "$GATEWAY/mcp" -H "Authorization: Bearer $DEMO_API_KEY" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
    -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$1\",\"params\":$params}"
}
call() { mcp tools/call "{\"name\":\"demo-tools__$1\",\"arguments\":$2}"; }
# The text of a tool result, or the error message of a refused call.
show() {
  local r text
  r=$(cat)
  if [[ $r == *'"error":{'* ]]; then
    sed -E 's/.*"message":"([^"]+)".*"dvara_code":"([^"]+)".*/REFUSED (\2): \1/' <<<"$r"
  else
    text=$(sed -E 's/.*"text":"(.*)"\}\],"isError".*/\1/' <<<"$r")
    text=${text#\\\"}; text=${text%\\\"}; text=${text//\\\\n/ }; text=${text//\\n/ }
    echo "$text"
  fi
}
step() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }

step "1. tools/list: the agent sees the demo server's tools, namespaced by server"
mcp tools/list | grep -oE '"name":"demo-tools__[a-z_]+"' | cut -d'"' -f4 | sed 's/^/   /'

step "2. get_order_status: a harmless tool. Allowed, and recorded."
printf '   '; call get_order_status '{"orderId":"A-1001"}' | show

step "3. lookup_customer: the server returns an email, a phone and a card number."
echo "   The gateway redacts them before the agent sees the result:"
printf '   '; call lookup_customer '{"customerId":"C-42"}' | show

step "4. delete_customer: a policy denies it. The server is never called."
printf '   '; call delete_customer '{"customerId":"C-42"}' | show

cat <<EOF

Every call above is in the audit trail. Open $FLIGHTDECK/mcp/tool-calls
and sign in as $OWNER_EMAIL / $OWNER_PASSWORD.

Point your own MCP client at it too: $GATEWAY/mcp with the header
  Authorization: Bearer $DEMO_API_KEY
EOF
