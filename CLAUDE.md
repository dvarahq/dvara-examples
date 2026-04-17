# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Reference Docker Compose stacks and integration samples for the [Dvara LLM Gateway](https://dvarahq.com) — a commercial product distributed as prebuilt images on `ghcr.io/dvarahq/dvara/*`. **There is no source code to build, lint, or test here.** Changes are almost always to `docker-compose.yml` files, `.env.example` files, or Markdown docs.

## Repository layout

Four self-contained Compose stacks under `docker-compose/`, each a copy-paste starting point rather than overlays:

- `quick-start/` — postgres + gateway-server + gateway-ui, OpenAI only
- `multi-provider/` — same services, OpenAI + Anthropic wired in, other providers commented
- `ollama/` — adds a local `ollama` service for offline inference
- `full/` — adds `mcp-proxy-server` for MCP tool governance

Each directory is meant to be `cd`'d into and run with `docker compose up -d` after copying `.env.example` → `.env`.

## Common operations

From inside any variant directory:

```bash
docker compose up -d
docker compose ps            # all services should reach "healthy"
docker compose logs -f gateway-server
docker compose down          # stop, keep pgdata volume
docker compose down -v       # stop and wipe postgres data
```

Smoke checks after `up`:
- `curl http://localhost:8080/status` — gateway
- `curl http://localhost:8090/actuator/health/liveness` — admin UI
- `curl http://localhost:8070/actuator/health/liveness` — MCP proxy (`full/` only)

## Architecture notes that matter when editing

- **PostgreSQL is mandatory** for every variant — there is no in-memory fallback. Any new stack must include it and wire `SPRING_DATASOURCE_*` env vars into every Dvara service.
- **All Dvara services share one database.** `gateway-server`, `gateway-ui`, and `mcp-proxy-server` all connect to the same `dvara` DB with the same credentials. Do not give them separate databases.
- **`GATEWAY_LICENSE_KEY` is required by every Dvara service**, not just the gateway. Missing it from any one service will break that container.
- **Service dependency chain is fixed**: postgres (healthy) → gateway-server (healthy) → gateway-ui and mcp-proxy-server. Preserve these `depends_on` + `condition: service_healthy` blocks when editing.
- **Provider keys belong only on `gateway-server`.** The UI and MCP proxy don't need them. When adding a provider to `multi-provider/` or `full/`, add the env var there only.
- **Fixed host ports**: 5432 (postgres), 8080 (gateway), 8090 (admin UI), 8070 (MCP proxy). These are referenced in the READMEs and smoke-test commands — keep them aligned if you change one.
- **Images are pulled by `:latest` tag.** Version pinning is intentionally not used in examples; the README documents that version tags (e.g. `1.0.0`) are also available.

## When adding a new variant

1. Create a new subdirectory under `docker-compose/` with a self-contained `docker-compose.yml` (not an overlay).
2. Include postgres, the required Dvara services, healthchecks, and the `depends_on` chain described above.
3. Add a top-of-file comment explaining the intent + usage, matching the style of the existing files.
4. Add a row to the Variants table in `docker-compose/README.md`.
