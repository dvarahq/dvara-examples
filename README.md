# Dvara Examples

Reference configurations, compose files, and SDK integration samples for the [Dvara LLM Gateway](https://dvarahq.com).

> **Latest release: [1.8.4](https://github.com/dvarahq/dvara-examples/releases/tag/1.8.4)** — compatible with Dvara `1.8.4`.

## Contents

| Directory | Description |
|---|---|
| **[docker-compose/](docker-compose/)** | Ready-to-run Docker Compose stacks (quick-start, multi-provider, ollama, with-email) |
| **[getting-started/](getting-started/)** | First-request scripts in Python and Node.js — basic chat, streaming, structured outputs, multi-provider |
| **[sdk-integrations/](sdk-integrations/)** | Framework examples — OpenAI SDK, LangChain, LiteLLM, Pydantic AI, Vercel AI, Spring AI, LangChain4j |

## Quick start

```bash
git clone https://github.com/dvarahq/dvara-examples.git
cd dvara-examples/docker-compose/quick-start
cp .env.example .env
# edit .env — set DVARA_ENROLMENT_SECRET and the other secrets (generate each one
# with `openssl rand -base64 32`), and OPENAI_API_KEY.
# DVARA_LICENSE_KEY can stay blank: every feature runs, limited to 3 workspaces
# and 100,000 calls a month, for non-production use.
docker compose up -d
```

Gateway ready at http://localhost:8080, DVARA Console at http://localhost:8090.

```bash
# Create a workspace and API key in the Console, then:
export DVARA_API_KEY="gw_<your-key>"

cd ../../getting-started/python
pip install -r requirements.txt
python dvara_test.py
```

## Compatibility

Each release of these examples is pinned to a specific Dvara LLM Gateway version. Use the matching examples release for your gateway version.

| Examples release | Compatible Dvara version |
|---|---|
| `1.8.4` | Dvara `1.8.4` |
| `1.8.1` | Dvara `1.8.1` |
| `1.8.0` | Dvara `1.8.0` |
| `1.7.0` | Dvara `1.7.0` |
| `1.6.0` | Dvara `1.6.0` |
| `1.5.0` | Dvara `1.5.0` |

Older examples releases are listed on the [releases page](https://github.com/dvarahq/dvara-examples/releases).

## Changelog

### [1.8.4](https://github.com/dvarahq/dvara-examples/releases/tag/1.8.4)

This release also covers Dvara 1.8.2 and 1.8.3. There was no examples release for either, so their changes are listed here.

- **Version bump** — every Compose stack, Kubernetes/Helm recipe and the jbang GKE tooling now pin `1.8.4` (chart `oci://ghcr.io/dvarahq/charts/dvara:1.8.4`). No renamed image, no new service, no moved port.
- **The gateway has no database (since 1.8.2).** Flightdeck is the only service that connects to PostgreSQL, so it starts first. The gateway enrols with it using a shared secret, gets its configuration from it, and sends its audit and usage records back through a spool on a volume. A gateway given `SPRING_DATASOURCE_URL` refuses to start.
- **Compose: set `DVARA_ENROLMENT_SECRET` (at least 32 characters) in `.env`.** Both services read the same value. The gateway keeps its files on a new `gateway-data` volume. `DVARA_LICENSE_KEY` is optional and set on Flightdeck only.
- **Kubernetes: the values files use the chart's real keys.** `gatewayServer:` is `llmGatewayServer:`, `mcpProxyServer:` is gone, and `secrets.gatewayServerApiKey` is `secrets.llmGatewayServerApiKey` (Secret key `llm-gateway-server-api-key`). The chart refuses the old names. Database settings are on `flightdeck.extraEnv` only. The chart generates the enrolment secret; with `secrets.existingSecret`, add the key `enrolment-shared-secret`. Gateways get a volume per pod (`llmGatewayServer.persistence.enabled`), which makes them a StatefulSet.
- **Multi-region: one Flightdeck per region.** A gateway enrols with the Flightdeck in its own release, and Flightdeck publishes configuration for its own region only. The Flightdecks share one database.
- **`DVARA_PII_TOKEN_ENCRYPTION_MASTER_PASSWORD` is set on Flightdeck only.** Flightdeck seals each workspace's PII key with it; the gateway fetches the keys from Flightdeck and needs no copy, so the Compose stacks no longer pass it to the gateway. Keep the value: losing it makes every PII token unreadable.
- **Generate every secret with `openssl rand -base64 32`.** Each `.env.example` now says so: one fresh value per secret, 32 bytes, never a placeholder. From 1.8.4 the A2A delegation guard derives its key from `DVARA_AUDIT_HMAC_SECRET` and refuses a placeholder or a secret under 32 bytes. These stacks run the default (dev) profile, so the gateway logs a WARN and runs the guard off; a production profile won't start.
- **No licence needed to try it.** Without one every feature runs, including MCP and A2A, limited to 3 workspaces and 100,000 calls a month, for non-production use.
- **Ollama stack: the `ollama` healthcheck works again.** The current `ollama/ollama` image has no `curl`, so the old check never passed and the stack never started. It now runs `ollama list`.
- **The DigitalOcean App Platform recipes are removed.** On DigitalOcean, use [DOKS](kubernetes/doks/) or a Droplet running one of the [Compose stacks](docker-compose/).

### [1.8.1](https://github.com/dvarahq/dvara-examples/releases/tag/1.8.1)

- **Version bump** — every Compose stack, Kubernetes/Helm recipe, the DigitalOcean recipe, and the jbang GKE tooling now pin `1.8.1` (chart `oci://ghcr.io/dvarahq/charts/dvara:1.8.1`).
- **Drop-in over 1.8.0.** Nothing in these recipes changes shape: no renamed image, no new service, no moved port.
- **One setting can stop the gateway starting.** If you turned the ML classifier on and set `provider=onnx-injection`, 1.8.1 removed that provider and the gateway refuses to start. Use `provider=defender`, whose model is in the image and needs no volume. None of these stacks sets it.

### [1.8.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.8.0)

- **Version bump** — every Compose stack, Kubernetes/Helm recipe, the DigitalOcean recipe, and the jbang GKE tooling now pin `1.8.0` (chart `oci://ghcr.io/dvarahq/charts/dvara:1.8.0`).
- **Every `/v1` call needs an API key.** Dvara 1.8.0 removed `DVARA_LLM_GATEWAY_REQUIRE_API_KEY`, so the Compose stacks no longer set it. Setting it to `false` on 1.8.0 stops the gateway starting.
- **Set `DVARA_AUDIT_HMAC_SECRET` before running a production profile.** Dvara 1.8.0 no longer generates the audit signing secret. These recipes run the default profile, where the gateway starts without one; a production profile refuses to start until every gateway and Flightdeck process has the same value.

### [1.7.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.7.0)

- **Version bump** — every Compose stack, Kubernetes/Helm recipe, the DigitalOcean recipe, and the jbang GKE tooling now pin `1.7.0`.
- **`dvara-llm-gateway` is now `dvara-gateway`.** The image was renamed; the old package still resolves to pre-release images, so repoint rather than leaving it.
- **`full/` lost two containers, and they are not coming back.** `dvara-mcp-gateway` (`8070`) and `dvara-a2a-gateway` (`8075`) are **retired images** — the MCP and A2A planes now run inside the gateway process. The paths are unchanged and moved to the gateway on `8080`. Repoint anything addressing `:8070` or `:8075`.
- **The Community / Enterprise image split is gone.** There are no `-ee` variants and no private packages: there is one artifact per application, all public, and a licence decides what runs. Unlicensed installs get policies, PII, guardrails, audit, budgets and cost attribution; a `DVARA-` envelope additionally activates the MCP and A2A paths. Nothing refuses to boot without one.
- **`full/` is folded into `quick-start/`.** Once the planes moved into the gateway it was the same three services plus a `DVARA_LICENSE_KEY`, so the directory named a topology that no longer existed. Run `quick-start/` and set a `DVARA-` envelope to get what `full/` gave you.

### [1.6.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.6.0)

- **Version bump** — every Compose stack, Kubernetes/Helm recipe, the DigitalOcean recipe, and the jbang GKE tooling now pin `1.6.0` (chart `oci://ghcr.io/dvarahq/charts/dvara:1.6.0`).
- **Documented the Community / Enterprise image split.** Since 1.5.0 the images are in two registries' worth of visibility: `dvara-llm-gateway` and `dvara-flightdeck` are **public**, while `dvara-llm-gateway-ee`, `dvara-flightdeck-ee`, `dvara-mcp-gateway`, and `dvara-a2a-gateway` are **private to customers**. Nothing in this repo said so, so the `full/` stack — which uses all four private images — failed on `docker compose pull` with an opaque denial for anyone without registry access. `full/` now has its own README stating the requirement up front, and the image table in [`docker-compose/README.md`](docker-compose/) is split public / private.
- **Corrected the `full/` stack description** — it has run five services since 1.3.0 (it gained the A2A plane then), but the variants table still listed four and omitted `dvara-a2a-gateway`.

### [1.5.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.5.0)

- **Version bump** — all Docker Compose stacks (`quick-start`, `multi-provider`, `ollama`, `full`, `with-email`) now pin `ghcr.io/dvarahq/dvara-*:1.5.0`.
- **License key now optional** — `.env.example` ships with `DVARA_LICENSE_KEY=` blank: blank runs Community Edition; set a `DVARA-` signed envelope for Enterprise.
- **Kubernetes image path fix** — corrected the image repository to `ghcr.io/dvarahq/dvara-llm-gateway` / `dvara-flightdeck` (removed the stray `/dvara/` path segment) across the DOKS, GKE, multi-region, and single-tenant values.

Older changes are in the notes of each release on the [releases page](https://github.com/dvarahq/dvara-examples/releases).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).

## License

MIT — see [LICENSE](LICENSE).
