# Docker Compose Examples

Ready-to-run stacks for the Dvara LLM Gateway. Each subdirectory is self-contained — `cd` into one, copy `.env.example` to `.env`, and run `docker compose up -d`.

All stacks include PostgreSQL — it's required by the gateway and there is no in-memory fallback.

## Variants

| Directory | Services | When to use |
|---|---|---|
| [`quick-start/`](quick-start) | postgres + dvara-gateway + dvara-flightdeck | Fastest path to a running gateway. OpenAI only. |
| [`multi-provider/`](multi-provider) | postgres + dvara-gateway + dvara-flightdeck | OpenAI + Anthropic out of the box. More providers (Gemini, Mistral, Cohere, Groq, Azure, Bedrock, Ollama) can be added by uncommenting env vars. |
| [`ollama/`](ollama) | postgres + dvara-gateway + dvara-flightdeck + ollama | Local models, no external LLM calls. |
| [`full/`](full) | postgres + dvara-gateway + dvara-flightdeck + dvara-mcp-gateway | Every Dvara component running together — adds the MCP proxy for agent tool governance. |
| [`with-email/`](with-email) | postgres + dvara-gateway + dvara-flightdeck | Same shape as `quick-start/` with transactional email (`log` / `resend` / `smtp`) + the delivery durability layer (retry / DLQ / idempotency) surfaced for tuning and inspection. |

## Quick start

```bash
cd quick-start
cp .env.example .env
# edit .env — set DVARA_LICENSE_KEY and OPENAI_API_KEY

docker compose up -d
docker compose ps
```

Then:

- Gateway: http://localhost:8080/actuator/health
- Flightdeck: http://localhost:8090 (first visit redirects to `/setup` to create the owner account)
- Playground: http://localhost:8090/playground (interactive prompt testing inside Flightdeck; disable in production with `DVARA_FLIGHTDECK_PLAYGROUND_ENABLED=false`)

## Requirements

- Docker 20.10+ with Compose v2
- A Dvara **self-hosted** license envelope (`DVARA-…` prefix, Ed25519-signed). The **Start Free Trial** button on [dvarahq.com](https://dvarahq.com) provisions a hosted SaaS account — it does **not** email a self-hosted envelope. For a self-hosted trial, fill in the [Book a demo](https://dvarahq.com/#book-demo) form (mention you need a trial license for a self-hosted install) or email [support@dvarahq.com](mailto:support@dvarahq.com). Typical turnaround is the same business day.
- For `quick-start/`, `multi-provider/`, `full/`: at least one provider API key
- For `ollama/`: no provider keys needed (local inference)

## Stopping

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete postgres volume
```

## Images

All images are published on GitHub Container Registry:

| Image | Description |
|---|---|
| `ghcr.io/dvarahq/dvara/dvara-llm-gateway:1.2.3` | Gateway server (port 8080) |
| `ghcr.io/dvarahq/dvara/dvara-flightdeck:1.2.3` | Admin dashboard (port 8090) |
| `ghcr.io/dvarahq/dvara/dvara-mcp-gateway:1.2.3` | MCP proxy server (port 8070) |

### Platform

The published images today are **`linux/amd64` only** — every Dvara service in these compose files carries an explicit `platform: linux/amd64` line so `docker compose pull` works out of the box on Apple Silicon (M-series Macs) and ARM-based cloud instances (Graviton, Ampere). On those hosts Docker runs the images under Rosetta / QEMU emulation — slower than native but functional. On Intel / AMD hosts the line is a no-op.

Native ARM builds are a planned follow-up. Once they land you can remove the `platform:` lines or leave them in place — the explicit pin still works against multi-arch manifests, it just stops being load-bearing.

Tags: `latest` (current release) or a version tag (e.g. `1.2.3`).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).
