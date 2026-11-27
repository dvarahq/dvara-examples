# Docker Compose Examples

Ready-to-run stacks for the Dvara LLM Gateway. Each subdirectory is self-contained — `cd` into one, copy `.env.example` to `.env`, and run `docker compose up -d`.

All stacks but `mcpworks-standalone/` include PostgreSQL — Flightdeck requires it and there is no in-memory fallback.

**Since 1.8.2 the gateway has no database.** Flightdeck is the only service that connects to
PostgreSQL, so it starts first. The gateway enrols with it using `DVARA_ENROLMENT_SECRET`, gets
its configuration from it, and sends its audit and usage records back through a spool on the
`gateway-data` volume. A change saved in Flightdeck reaches the gateway in about 10 seconds.

## Variants

| Directory | Services | When to use |
|---|---|---|
| [`quick-start/`](quick-start) | postgres + dvara-flightdeck + dvara-gateway | Fastest path to a running gateway. OpenAI only. |
| [`mcp-quickstart/`](mcp-quickstart) | postgres + dvara-flightdeck + dvara-gateway + a JBang MCP server | Governed MCP tool calls in one command: allowed, PII-redacted and policy-denied. No licence or provider key. |
| [`mcpworks-standalone/`](mcpworks-standalone) | MCPWorks runtime (`mcp-run:1.17.1`) + a small nginx API | One MCP server from one `server.yaml`, with no Flightdeck, no gateway and no database. Needs pull access to `ghcr.io/dvarahq/mcp-run`. |
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

Before the first call (since 1.15.0):

1. In Flightdeck, create a workspace under **Access → Workspaces**.
2. Under **Runtime → Environments**, give the `main` environment to that workspace. `main` is the
   gateway's cluster (`DVARA_CLUSTER_NAME`); until a workspace has its environment, the gateway
   accepts none of that workspace's keys.
3. In the workspace, create a key under **Keys → New Key** and call `http://localhost:8080/v1/...` with it.

If the gateway's log says `cluster_required` or `cluster_unknown`, `DVARA_CLUSTER_NAME` is missing
or differs between the two services.

## Set up a model before the first call (since 1.17.0)

**A gateway serves only the models its cluster lists.** A new cluster lists none, so until you do
the steps below every model call is refused:

```json
{"error":{"message":"Model 'gpt-4o' is not served by this gateway's cluster (main). ...",
  "code":"not_found","reason":"model_not_on_cluster","model":"gpt-4o","cluster":"main"}}
```

In 1.17.0 this answer comes with HTTP status `502`. `GET /v1/models` lists nothing in 1.17.0, even
after you add models.

You do this in Flightdeck's pages. There is no API or file for it yet. A later release gives
every new cluster a default mock model, so the first call works without these steps.

1. **Credentials** (**Traffic → Credentials**, `/credentials`). Skip this step when the provider key
   is in the stack's `.env`, as in these stacks: the gateway reads it from its environment. To keep
   the key in Flightdeck instead, choose **Add Credential**, pick the provider, paste the key and
   choose **Create credential**. **Test connection** checks the key on a connected gateway of the
   cluster.
2. **Providers** (**Traffic → Providers**, `/providers`). Choose **Add provider**, pick the type
   (for example `openai`), set a base URL if the type needs one, and choose **Create provider**. Do
   this even when the key is in `.env`. A key in the gateway's environment shows on this page as an
   **Env** provider. It is read-only and wins over the one you add here, but the cluster offers
   models only for providers added here.
3. **The provider's models** (the **Models** link on the provider's row,
   `/providers/<type>/models`). Add each model callers will send:
   - Copy the model's JSON from `https://dvarahq.com/models/<type>` (for example
     [dvarahq.com/models/openai](https://dvarahq.com/models/openai)), paste it under **Paste JSON**
     and choose **Fill the form**. Or type the fields. For Ollama there is no page there: type the
     model name as Ollama lists it (for example `llama3.2`), `0` for both prices and its context size.
   - Choose **Add as draft**. A draft is not served.
   - Choose **Release** on the model's row. A model can be released only with a context size, an
     input price and an output price. Prices come from the released models: there is no Pricing
     page to type them into any more.
4. **The cluster** (**Runtime → Clusters → main**, `/clusters/main`). In the **Models** section,
   pick the model under **Model to add** and choose **Add model**. When several providers serve
   the same model, the first serves and the others are backups, in order. **Edit routing** on the
   row sets the strategy, weights, canary and fallbacks: routes are set per model here, and the
   install-wide Routes page is gone.

The change reaches the gateway in about 10 seconds. Then call the model by the name you added, with
no provider prefix: `llama3.2`, not `ollama/llama3.2`.

What to add for each stack:

| Stack | Provider type | Models the examples call |
|---|---|---|
| `quick-start/`, `with-email/` | `openai` | `gpt-4o` (the [getting-started](../getting-started/) scripts) |
| `multi-provider/` | `openai`, `anthropic` | `gpt-4o`, `claude-sonnet-4-5` |
| `ollama/` | `ollama`, base URL `http://ollama:11434` | the model you pulled, for example `llama3.2` |

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

All images are on GitHub Container Registry. The gateway and Flightdeck images are **public** — pull
anonymously, no account and no `docker login`.

| Image | Description |
|---|---|
| `ghcr.io/dvarahq/dvara-gateway:1.17.0` | Gateway server — LLM on `8080`, and the MCP and A2A paths |
| `ghcr.io/dvarahq/dvara-flightdeck:1.17.0` | DVARA Console / admin dashboard (port `8090`) |

| `ghcr.io/dvarahq/mcp-run:1.17.1` | MCPWorks runtime, used only by `mcpworks-standalone/`. **Not public yet**: pulling it needs `docker login ghcr.io` with an account that has access. |

Every stack but `mcpworks-standalone/` uses the first two. **There is one artifact per application**, and every feature
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

Tags: `latest` (current release) or a version tag (e.g. `1.17.0`).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).
