#!/usr/bin/env bash
# Makes one MCP tool call to the standalone MCPWorks runtime, as an agent would:
# initialize, list the tools, then call get_order_status. Run `docker compose up -d` first.
set -euo pipefail
cd "$(dirname "$0")"
[[ -f .env ]] || { echo 'Write .env first: echo "MCP_CALLER_KEY=$(openssl rand -hex 24)" > .env' >&2; exit 1; }
set -a; source .env; set +a

MCP=http://localhost:${MCP_PORT:-8095}/mcp
HEADERS=(-H "Authorization: Bearer $MCP_CALLER_KEY" -H 'Content-Type: application/json'
         -H 'Accept: application/json, text/event-stream')

# Wait until the runtime serves the file.
for _ in $(seq 1 60); do
  curl -sf -o /dev/null "http://localhost:${MCP_PORT:-8095}/actuator/health/readiness" && break
  sleep 2
done

# 1. initialize: the runtime answers with a session id, sent on every later request.
session=$(curl -s -D - -o /dev/null "$MCP" "${HEADERS[@]}" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"call.sh","version":"1"}}}' \
  | tr -d '\r' | awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}')
[[ -n $session ]] || { echo "initialize failed. Is the key in .env the one the runtime started with?" >&2; exit 1; }
HEADERS+=(-H "Mcp-Session-Id: $session")
curl -s -o /dev/null "$MCP" "${HEADERS[@]}" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# The runtime answers as server-sent events. 1.17.1 closes a stream without its last
# chunk, so curl may exit 18 though the answer is complete: `|| true` keeps going.
post() { curl -s "$MCP" "${HEADERS[@]}" -d "$1" || true; }

# 2. tools/list
echo "Tools:"
post '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | grep -oE '"name":"[a-z_]+"' | cut -d'"' -f4 | sed 's/^/  /'

# 3. tools/call: print the answer's data line.
echo "get_order_status A-1001:"
answer=$(post '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_order_status","arguments":{"id":"A-1001"}}}')
grep '^data:' <<<"$answer" | sed 's/^data:/  /'
grep -q '"isError":false' <<<"$answer" || { echo "The call failed. See: docker compose logs mcp-run" >&2; exit 1; }
