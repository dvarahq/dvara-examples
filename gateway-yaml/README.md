# gateway.yaml — config samples

Six sample `gateway.yaml` configurations covering common shapes. Drop one in the gateway's working directory (or point at it via env var) to bootstrap providers, routes, tenants, and API keys without going through the Console.

| File | Shape |
|---|---|
| **`gateway.openai.yaml`** | Single OpenAI provider, one auto-generated dev key. Smallest possible boot. |
| **`gateway.anthropic.yaml`** | Single Anthropic provider. |
| **`gateway.ollama.yaml`** | Local Ollama at `http://localhost:11434`. Offline-capable. |
| **`gateway.multi-provider.yaml`** | OpenAI + Anthropic + Gemini together with prefix routing. |
| **`gateway.full.yaml`** | All six providers (incl. Bedrock and Mock), weighted route with fallback, multi-tenant API keys, rate limits. The "documentation by example" version. |
| **`gateway.bootstrap.yaml`** | Database-seed shape — `tenants:`, `api_keys:`, `routes:` at startup via `DVARA_BOOTSTRAP_FILE`. Idempotent on subsequent boots. |

## How the gateway loads these

Two distinct loaders:

1. **`gateway.yaml`** in the working directory is read by `GatewayConfigEnvironmentPostProcessor` **before** the Spring context starts. Provider entries are translated into `dvara.llm-gateway.providers.*` properties, rate-limit entries into `dvara.llm-gateway.rate-limit.*` properties, and added with **lowest priority** so explicit env vars + `application.yml` always win.
2. **Bootstrap files** (the `gateway.bootstrap.yaml` shape) are loaded by `BootstrapLoader` **after** Flyway has migrated the schema, and only when `DVARA_BOOTSTRAP_FILE` is set + the database is empty (config version = 0). Subsequent boots skip the seed. Use this to provision an install in one step instead of clicking through the Console.

The two shapes share `${VAR}` and `${VAR:-default}` env-var resolution syntax.

## Usage

```bash
# Side-loaded gateway.yaml — providers + routes:
cp gateway-yaml/gateway.openai.yaml ./gateway.yaml
OPENAI_API_KEY=sk-… docker compose -f docker-compose/quick-start/docker-compose.yml up -d

# OR via Helm — mount as a ConfigMap and reference from gatewayServer.gatewayConfig
# (see ../kubernetes/single-tenant/values.yaml § gatewayConfig)

# Bootstrap shape — tenants + api_keys + routes:
cp gateway-yaml/gateway.bootstrap.yaml ./bootstrap.yaml
DVARA_BOOTSTRAP_FILE=./bootstrap.yaml \
  ACME_API_KEY=$(openssl rand -hex 32) \
  docker compose -f docker-compose/quick-start/docker-compose.yml up -d
```

The bootstrap shape **prints any auto-generated keys to stdout at startup** (look for `BootstrapLoader` log lines). Capture them; they're not visible later — only the SHA-256 hash is stored.

## Schema notes

- `providers[].name` is the gateway-internal alias; `providers[].type` is the implementation class. The name appears in routes (`route.provider`/`route.providers[].provider`) and is what the Console / Automation API surfaces.
- `routes[].model` is a glob pattern matched against the incoming request's `model` field. Use `gpt*` to catch every GPT family, `mock/*` for the Mock provider's namespace, etc.
- `routes[].strategy` values: `model-prefix` (default), `round-robin`, `weighted`, `latency-aware`, `cost-aware`. `weighted` is the only one that requires `providers[].weight`.
- `api_keys[].generate: true` mints a `gw_…` key at boot and prints it once. `api_keys[].key: ${VAR}` reuses a known value (useful for CI / fixtures).
- `rate_limits.requests_per_minute` + `rate_limits.tokens_per_minute` map 1:1 to per-API-key sliding-60s counters; both are optional.

## Related

- **[../docker-compose/](../docker-compose/)** — base Compose stacks that consume these `gateway.yaml` files.
- **[../kubernetes/](../kubernetes/)** — Helm chart reference values. The chart's `gatewayServer.gatewayConfig:` field accepts the same shape inline.
- **[../digitalocean/](../digitalocean/)** — DO App Platform deploys.
- **[../datadog/](../datadog/)** + **[../grafana/](../grafana/)** — observability stacks.
- **DVARA configuration reference** — see the [Configuration docs](https://dvarahq.com/docs/deployment/configuration) for every `dvara.*` property.
