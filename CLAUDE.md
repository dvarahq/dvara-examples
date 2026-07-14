# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Reference Docker Compose stacks and integration samples for the [Dvara LLM Gateway](https://dvarahq.com) — a commercial product distributed as prebuilt images on `ghcr.io/dvarahq/dvara/*`. **There is no source code to build, lint, or test here.** Changes are almost always to `docker-compose.yml` files, `.env.example` files, or Markdown docs.

## Repository layout

Five self-contained Compose stacks under `docker-compose/`, each a copy-paste starting point rather than overlays:

- `quick-start/` — postgres + dvara-gateway + dvara-flightdeck, OpenAI only
- `multi-provider/` — same services, OpenAI + Anthropic wired in, other providers commented
- `ollama/` — adds a local `ollama` service for offline inference
- `full/` — adds `dvara-mcp-gateway` for MCP tool governance
- `with-email/` — quick-start shape plus transactional email (`log` / `resend` / `smtp`) and the delivery durability layer (retry / DLQ / idempotency)

Each directory is meant to be `cd`'d into and run with `docker compose up -d` after copying `.env.example` → `.env`.

## Common operations

From inside any variant directory:

```bash
docker compose up -d
docker compose ps            # all services should reach "healthy"
docker compose logs -f dvara-gateway
docker compose down          # stop, keep pgdata volume
docker compose down -v       # stop and wipe postgres data
```

Smoke checks after `up`:
- `curl http://localhost:8080/actuator/health` — gateway
- `curl http://localhost:8090/actuator/health/liveness` — admin console (Flightdeck)
- `curl http://localhost:8070/actuator/health/liveness` — MCP proxy (`full/` only)

## Architecture notes that matter when editing

- **PostgreSQL is mandatory** for every variant — there is no in-memory fallback. Any new stack must include it and wire `SPRING_DATASOURCE_*` env vars into every Dvara service.
- **All Dvara services share one database.** `dvara-gateway`, `dvara-flightdeck`, and `dvara-mcp-gateway` all connect to the same `dvara` DB with the same credentials. Do not give them separate databases.
- **`DVARA_LICENSE_KEY` is required by every Dvara service**, not just the gateway. Missing it from any one service will break that container.
- **Service dependency chain is fixed**: postgres (healthy) → dvara-gateway (healthy) → dvara-flightdeck and dvara-mcp-gateway. Preserve these `depends_on` + `condition: service_healthy` blocks when editing.
- **Provider keys belong only on `dvara-gateway`.** The Flightdeck admin console and MCP proxy don't need them. When adding a provider to `multi-provider/` or `full/`, add the env var there only.
- **Fixed host ports**: 5432 (postgres), 8080 (gateway), 8090 (admin console), 8070 (MCP proxy). These are referenced in the READMEs and smoke-test commands — keep them aligned if you change one.
- **Images are pinned to an explicit version tag (`:1.3.0`)** in every stack — not `:latest` — so a copied stack is reproducible. Bump the pin in each `docker-compose.yml` when a new release ships.

## When adding a new variant

1. Create a new subdirectory under `docker-compose/` with a self-contained `docker-compose.yml` (not an overlay).
2. Include postgres, the required Dvara services, healthchecks, and the `depends_on` chain described above.
3. Add a top-of-file comment explaining the intent + usage, matching the style of the existing files.
4. Add a row to the Variants table in `docker-compose/README.md`.
