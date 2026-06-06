# DigitalOcean App Platform

Deploy DVARA AI Gateway on [DigitalOcean App Platform](https://www.digitalocean.com/products/app-platform).

Two starting points:

| Shape | When to use | What's included |
|---|---|---|
| **[smoke/](smoke/)** | "Does DVARA boot on App Platform?" — fastest possible answer | Gateway-server only, Mock provider, no managed PG. Single service, ~60s deploy. |
| **[production/](production/)** | Real deploy: gateway + Flightdeck Console + Managed PG | Three services, DO Managed PostgreSQL addon, BYOK credential model (no provider keys in env vars). |

Both shapes require four secrets at deploy time (each `openssl rand -base64 32` except the license):

| Env var | Purpose | Notes |
|---|---|---|
| `DVARA_LICENSE_KEY` | Signed DVARA license envelope | Production-signed `DVARA-…` string. Request from the release manager. Validated at boot. |
| `DVARA_ENCRYPTION_MASTER_PASSWORD` | AES-256-GCM key for `ENCRYPTED`-mode provider credentials in the DB | **Loss is unrecoverable.** Escrow offline — password manager + printed copy in a safe. |
| `DVARA_ACTUATOR_API_KEY` | Bearer for `/actuator/gateway-status` (the License Console + flightdeck's status probe) | The Bearer flightdeck sends on every operator-facing actuator call. |
| `DVARA_ACTUATOR_METRICS_API_KEY` | Bearer for `/actuator/prometheus` | **Must differ** from `DVARA_ACTUATOR_API_KEY` — principle of least privilege. |

## One-click deploy

[**Deploy smoke variant →**](https://cloud.digitalocean.com/apps/new?repo=https://github.com/dvarahq/dvara-examples/tree/main/digitalocean/smoke)
[**Deploy production variant →**](https://cloud.digitalocean.com/apps/new?repo=https://github.com/dvarahq/dvara-examples/tree/main/digitalocean/production)

App Platform reads the `app.yaml` from the linked subdirectory, prompts for the four secrets, and provisions the stack. The production variant also creates the Managed PG addon.

## After the first deploy

1. Set the four secrets via App Platform → Settings → App-Level Environment Variables.
2. Open the Console at `https://<your-app>.ondigitalocean.app/` (the flightdeck service URL, only present on the production variant).
3. Walk `/setup` to create the founding owner.
4. Create your first tenant + API key via Console → Tenants → New tenant.
5. Either: set `MOCK_PROVIDER_ENABLED=true` and route to `mock/*` models, OR add provider credentials via Console → Credentials → Add credential (BYOK; either `ENCRYPTED` with the upstream key pasted, or `REFERENCE` with a vault pointer).
6. Fire a chat completion: `curl -H 'Authorization: Bearer gw_…' https://<gateway-server-url>/v1/chat/completions -d '{"model":"mock/test","messages":[{"role":"user","content":"hi"}]}'`

## Pinning the image tag

Both variants default to `:latest`. For production stability, replace every `image.tag: latest` with the published tag — e.g. `1.0.0` after the GA tag fires. Check the available tags at the [GHCR package page](https://github.com/orgs/dvarahq/packages/container/package/dvara%2Fdvara-llm-gateway).

## Provider keys — why they're NOT in env vars on the production variant

The production shape uses the **BYOK credential model** (`docs/claude/features.md` § BYOK Credential Model in the source repo). Tenant provider keys live in the database as AES-256-GCM-encrypted rows in `dvara_main.provider_credentials`, not as `OPENAI_API_KEY` env vars on the gateway-server pod. Two reasons:

1. **Per-tenant isolation.** Different tenants can BYO different OpenAI keys. Env-var keys are global.
2. **Rotation without restart.** `POST /v1/admin/credentials/{id}/rotate` swaps the key in-process. Env-var keys require a redeploy.

For the smoke variant, env-var provider keys would work (single tenant, no isolation needed). They're omitted to keep the smoke shape symmetric with production — both use Mock by default. Add `OPENAI_API_KEY` as a SECRET env on the gateway-server service if you want OpenAI routing on the smoke deploy without going through the Console.

## Related

- **[../docker-compose/](../docker-compose/)** — local Docker Compose stacks (quick-start, multi-provider, ollama, full)
- **[../getting-started/](../getting-started/)** — first-request scripts after the deploy is up
- **[../sdk-integrations/](../sdk-integrations/)** — OpenAI SDK / LangChain / LiteLLM / Spring AI examples
