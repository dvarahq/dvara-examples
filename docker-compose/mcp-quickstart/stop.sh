#!/usr/bin/env bash
# Stops the quickstart. --reset also deletes the database, the gateway's data and .env,
# so the next ./start.sh begins from nothing.
set -euo pipefail
cd "$(dirname "$0")"

# The demo MCP server: jbang hands over to a java process, so match the class name.
pkill -f 'demo.DemoMcpServer|DemoMcpServer.java' 2>/dev/null || true

if [[ ${1:-} == --reset ]]; then
  docker compose down -v
  rm -rf .env .quickstart
else
  docker compose down
fi
