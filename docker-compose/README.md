# Docker Compose Examples

Ready-to-run stacks for the Dvara LLM Gateway. Each subdirectory is self-contained — `cd` into one, copy `.env.example` to `.env`, and run `docker compose up -d`.

All stacks include PostgreSQL — Flightdeck requires it and there is no in-memory fallback.

**Since 1.8.2 the gateway has no database.** Flightdeck is the only service that connects to
PostgreSQL, so it starts first. The gateway enrols with it using `DVARA_ENROLMENT_SECRET`, gets
its configuration from it, and sends its audit and usage records back through a spool on the
`gateway-data` volume. A change saved in Flightdeck reaches the gateway in about 10 seconds.

## Variants

| Directory | Services | When to use |
|---|---|---|
| [`quick-start/`](quick-start) | postgres + dvara-flightdeck + dvara-gateway | Fastest path to a running gateway. OpenAI only. |
| [`multi-provider/`](multi-provider) | postgres + dvara-flightdeck + dvara-gateway | OpenAI + Anthropic out of the box. More providers (Gemini, Mistral, Cohere, Groq, Azure, Bedrock, Ollama) can be added by uncommenting env vars. |
| [`ollama/`](ollama) | postgres + dvara-flightdeck + dvara-gateway + ollama | Local models, no external LLM calls. |
| [`with-email/`](with-email) | postgres + dvara-flightdeck + dvara-gateway | Same shape as `quick-start/` with transactional email (`log` / `resend` / `smtp`) + the delivery durability layer (retry / DLQ / idempotency) surfaced for tuning and inspection. |

## Quick start

```bash
cd quick-start
cp .env.example .env
# edit .env — set DVARA_ENROLMENT_SECRET, the other secrets, and OPENAI_API_KEY

docker compose up -d
docker compose ps
```

Then:

- Gateway: http://localhost:8080/actuator/health
- Flightdeck: http://localhost:8090 (first visit redirects to `/setup` to create the owner account)
- Playground: http://localhost:8090/playground (interactive prompt testing inside Flightdeck; disable in production with `DVARA_FLIGHTDECK_PLAYGROUND_ENABLED=false`)

## Requirements

- Docker 20.10+ with Compose v2
- No licence to try it. Without one every feature runs, limited to 3 workspaces and 100,000 calls a month, for non-production use. For production, a `DVARA-` licence from your account team lifts the limits: [Book a demo](https://dvarahq.com/#book-demo) or email [support@dvarahq.com](mailto:support@dvarahq.com).
- For `quick-start/`, `multi-provider/`: at least one provider API key
- For `ollama/`: no provider keys needed (local inference)

## Stopping

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete the postgres and gateway-data volumes
```

## Images

All images are on GitHub Container Registry and **all of them are public** — pull anonymously, no
account and no `docker login`.

| Image | Description |
|---|---|
| `ghcr.io/dvarahq/dvara-gateway:1.8.4` | Gateway server — LLM on `8080`, and the MCP and A2A paths |
| `ghcr.io/dvarahq/dvara-flightdeck:1.8.4` | DVARA Console / admin dashboard (port `8090`) |

Every stack here uses those two. **There is one artifact per application**, and every feature
runs in it, including the MCP and A2A planes:

- **No licence** — limited to 3 workspaces and 100,000 calls a month, for non-production use.
- **A `DVARA-` licence** — no limits. Set it in `.env` before the first start, or on Flightdeck's
  Licence page at any time; no restart is needed.

### Two retired images, and the stack that went with them

Before 1.7.0 this table also listed `dvara-mcp-gateway` (`8070`) and `dvara-a2a-gateway` (`8075`),
and a fifth stack — `full/` — ran them as separate containers. **Both images are retired and those
ports no longer exist**: the planes moved into the gateway process. Repoint anything addressing
`:8070` or `:8075` at the gateway on `:8080`; the paths themselves are unchanged.

**`full/` is gone too, and was folded into `quick-start/`.** Once the planes moved, it was the same
three services plus a `DVARA_LICENSE_KEY` — a directory whose name described a topology that no
longer existed. To get what `full/` used to give you: run `quick-start/` and set a `DVARA-` envelope
in `.env` if you need more than the unlicensed limits. Nothing else differs.

The earlier public/private split is gone with them: there are no `-ee` image variants any more.

### Platform

The published images today are **`linux/amd64` only** — every Dvara service in these compose files carries an explicit `platform: linux/amd64` line so `docker compose pull` works out of the box on Apple Silicon (M-series Macs) and ARM-based cloud instances (Graviton, Ampere). On those hosts Docker runs the images under Rosetta / QEMU emulation — slower than native but functional. On Intel / AMD hosts the line is a no-op.

Native ARM builds are a planned follow-up. Once they land you can remove the `platform:` lines or leave them in place — the explicit pin still works against multi-arch manifests, it just stops being load-bearing.

Tags: `latest` (current release) or a version tag (e.g. `1.8.4`).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).
