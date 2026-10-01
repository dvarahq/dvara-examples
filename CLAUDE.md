# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Reference Docker Compose stacks and integration samples for the [Dvara LLM Gateway](https://dvarahq.com) — a commercial product distributed as prebuilt images on `ghcr.io/dvarahq/dvara/*`. **There is no source code to build, lint, or test here.** Changes are almost always to `docker-compose.yml` files, `.env.example` files, or Markdown docs.

## Repository layout

Four self-contained Compose stacks under `docker-compose/`, each a copy-paste starting point rather than overlays:

- `quick-start/` — postgres + dvara-flightdeck + dvara-gateway, OpenAI only
- `multi-provider/` — same services, OpenAI + Anthropic wired in, other providers commented
- `ollama/` — adds a local `ollama` service for offline inference
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
- `curl http://localhost:8080/mcp/` — the MCP plane, on the gateway, with or without a licence (the bare path answers `404`: it names no server). Port 8070 is RETIRED

## Architecture notes that matter when editing

- **PostgreSQL is mandatory** for every variant, and **only `dvara-flightdeck` connects to it** (since 1.8.2). Wire `SPRING_DATASOURCE_*` into Flightdeck only. A gateway given `SPRING_DATASOURCE_URL` refuses to start.
- **The gateway enrols with Flightdeck.** Both services carry the same `DVARA_ENROLMENT_SECRET` (at least 32 characters): Flightdeck as `DVARA_INTERNAL_ENROLMENT_SHARED_SECRET`, the gateway as `DVARA_DATA_PLANE_CONTROL_PLANE_ENROLMENT_SHARED_SECRET`, with `DVARA_DATA_PLANE_CONTROL_PLANE_BASE_URL: https://dvara-flightdeck:8443`. The gateway keeps its spool, config snapshot and certificate on the `gateway-data` volume.
- **`DVARA_LICENSE_KEY` is optional and belongs on Flightdeck only.** Without it every feature runs, limited to 3 workspaces and 100,000 calls a month. The gateway gets the licence from Flightdeck.
- **`DVARA_AUDIT_HMAC_SECRET` and `DVARA_ENCRYPTION_MASTER_PASSWORD` must be the same on both services.**
- **`DVARA_PII_TOKEN_ENCRYPTION_MASTER_PASSWORD` goes on Flightdeck only.** Flightdeck seals each workspace's PII key with it; the gateway fetches the keys from Flightdeck and needs no copy. Don't add it to the gateway.
- **Every secret in `.env.example` is a placeholder.** The comments tell the reader to generate each one with `openssl rand -base64 32`. Never put a real value in an `.env.example`. From 1.8.4 the A2A delegation guard refuses a placeholder or a secret under 32 bytes as `DVARA_AUDIT_HMAC_SECRET`: on the default (dev) profile the gateway logs a WARN and runs the guard off; a production profile won't start.
- **Service dependency chain is fixed**: postgres (healthy) → dvara-flightdeck (healthy) → dvara-gateway. Flightdeck's healthcheck uses **liveness**, not readiness: its readiness waits for the gateway, which waits for Flightdeck. Preserve these `depends_on` + `condition: service_healthy` blocks when editing.
- **Provider keys belong only on `dvara-gateway`.** The Flightdeck admin console does not need them. When adding a provider to `multi-provider/`, add the env var there only.
- **Fixed host ports**: 5432 (postgres), 8080 (gateway — LLM, and the /mcp and /a2a paths), 8090 (admin console). 8070 and 8075 are RETIRED: those services no longer exist. These are referenced in the READMEs and smoke-test commands — keep them aligned if you change one.
- **Images are pinned to an explicit version tag (`:1.8.5`)** in every stack — not `:latest` — so a copied stack is reproducible. Bump the pin in each `docker-compose.yml` when a new release ships.

## When adding a new variant

1. Create a new subdirectory under `docker-compose/` with a self-contained `docker-compose.yml` (not an overlay).
2. Include postgres, the required Dvara services, healthchecks, the `gateway-data` volume, and the `depends_on` chain described above.
3. Add a top-of-file comment explaining the intent + usage, matching the style of the existing files.
4. Add a row to the Variants table in `docker-compose/README.md`.
