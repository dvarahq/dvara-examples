# Docker Compose Examples

Ready-to-run stacks for the Dvara LLM Gateway. Each subdirectory is self-contained — `cd` into one, copy `.env.example` to `.env`, and run `docker compose up -d`.

All stacks include PostgreSQL — it's required by the gateway and there is no in-memory fallback.

## Variants

| Directory | Services | When to use |
|---|---|---|
| [`quick-start/`](quick-start) | postgres + gateway-server + gateway-ui | Fastest path to a running gateway. OpenAI only. |
| [`multi-provider/`](multi-provider) | postgres + gateway-server + gateway-ui | OpenAI + Anthropic out of the box. More providers (Gemini, Mistral, Cohere, Groq, Azure, Bedrock, Ollama) can be added by uncommenting env vars. |
| [`ollama/`](ollama) | postgres + gateway-server + gateway-ui + ollama | Local models, no external LLM calls. |
| [`full/`](full) | postgres + gateway-server + gateway-ui + mcp-proxy | Every Dvara component running together — adds the MCP proxy for agent tool governance. |

## Quick start

```bash
cd quick-start
cp .env.example .env
# edit .env — set GATEWAY_LICENSE_KEY and OPENAI_API_KEY

docker compose up -d
docker compose ps
```

Then:

- Gateway: http://localhost:8080/status
- Admin UI: http://localhost:8090 (first visit redirects to `/setup` to create the owner account)
- Test panel: http://localhost:8080/try

## Requirements

- Docker 20.10+ with Compose v2
- A Dvara license key — request one at [dvarahq.com](https://dvarahq.com)
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
| `ghcr.io/dvarahq/dvara/dvara-llm-gateway:latest` | Gateway server (port 8080) |
| `ghcr.io/dvarahq/dvara/dvara-admin-app:latest` | Admin dashboard (port 8090) |
| `ghcr.io/dvarahq/dvara/dvara-mcp-gateway:latest` | MCP proxy server (port 8070) |

Tags: `latest` (current release) or a version tag (e.g. `1.0.0`).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).
