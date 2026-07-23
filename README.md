# Dvara Examples

Reference configurations, compose files, and SDK integration samples for the [Dvara LLM Gateway](https://dvarahq.com).

> **Latest release: [1.5.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.5.0)** — compatible with Dvara LLM Gateway `1.5.0`.

## Contents

| Directory | Description |
|---|---|
| **[docker-compose/](docker-compose/)** | Ready-to-run Docker Compose stacks (quick-start, multi-provider, full, ollama, with-email) |
| **[getting-started/](getting-started/)** | First-request scripts in Python and Node.js — basic chat, streaming, structured outputs, multi-provider |
| **[sdk-integrations/](sdk-integrations/)** | Framework examples — OpenAI SDK, LangChain, LiteLLM, Pydantic AI, Vercel AI, Spring AI, LangChain4j |

## Quick start

```bash
git clone https://github.com/dvarahq/dvara-examples.git
cd dvara-examples/docker-compose/quick-start
cp .env.example .env
# edit .env — set DVARA_LICENSE_KEY and OPENAI_API_KEY
docker compose up -d
```

Gateway ready at http://localhost:8080, DVARA Console at http://localhost:8090.

```bash
# Create a tenant and API key in the Console, then:
export DVARA_API_KEY="gw_<your-key>"

cd ../../getting-started/python
pip install -r requirements.txt
python dvara_test.py
```

## Compatibility

Each release of these examples is pinned to a specific Dvara LLM Gateway version. Use the matching examples release for your gateway version.

| Examples release | Compatible Dvara version |
|---|---|
| `1.5.0` | Dvara `1.5.0` |
| `1.4.0` | Dvara `1.4.0` |
| `1.3.0` | Dvara `1.3.0` |
| `1.2.5` | Dvara `1.2.5` |
| `1.2.4` | Dvara `1.2.4` |
| `1.2.3` | Dvara `1.2.3` |
| `1.2.2` | Dvara `1.2.2` |
| `1.2.1` | Dvara `1.2.1` |
| `1.2.0` | Dvara `1.2.0` |
| `1.1.0` | Dvara `1.1.0` |
| `1.0.1` | Dvara `1.0.1` |

## Changelog

### [1.5.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.5.0)

- **Version bump** — all Docker Compose stacks (`quick-start`, `multi-provider`, `ollama`, `full`, `with-email`) now pin `ghcr.io/dvarahq/dvara-*:1.5.0`.
- **License key now optional** — `.env.example` ships with `DVARA_LICENSE_KEY=` blank: blank runs Community Edition; set a `DVARA-` signed envelope for Enterprise.
- **Kubernetes image path fix** — corrected the image repository to `ghcr.io/dvarahq/dvara-llm-gateway` / `dvara-flightdeck` (removed the stray `/dvara/` path segment) across the DOKS, GKE, multi-region, and single-tenant values.

### [1.4.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.4.0)

- Version bump `1.3.0` → `1.4.0` across all Compose stacks, Kubernetes/Helm recipes, the DigitalOcean recipe, and the jbang GKE tooling.
- Removed stale "once cut" wording from the DOKS recipe so its pinning guidance matches the GKE recipe.

### [1.3.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.3.0)

- Version bump `1.2.5` → `1.3.0`.
- **Added the A2A governance plane** (`dvara-a2a-gateway`, port 8075) to the `full` Compose stack — it now demos all four planes (gateway + admin UI + MCP proxy + A2A proxy + PostgreSQL).

### [1.2.5](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.5) · [1.2.4](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.4) · [1.2.3](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.3) · [1.2.2](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.2) · [1.2.1](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.1)

- Routine version bumps — each pins all Compose stacks, Kubernetes recipes, the DigitalOcean recipe, and the jbang tooling to the matching Dvara patch release.

### [1.2.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.2.0)

- Version bump to `1.2.0`.
- Config hot-reload docs moved from PostgreSQL `NOTIFY`/`LISTEN` to config-version polling (`dvara.config.poll-interval-ms`), which is pooler-agnostic.
- Grafana infrastructure dashboard metrics renamed to `gateway_config_poller_*` to match the new polling mechanism.

### [1.1.0](https://github.com/dvarahq/dvara-examples/releases/tag/1.1.0)

- Version bump to `1.1.0` across the Compose stacks and Kubernetes/Helm recipes.

### [1.0.1](https://github.com/dvarahq/dvara-examples/releases/tag/1.0.1)

- Bumped image pins `1.0.0` → `1.0.1` across Docker Compose, Kubernetes, and DigitalOcean examples.
- Added the release ↔ Dvara version compatibility table to the root README.

### [1.0.0-GA](https://github.com/dvarahq/dvara-examples/releases/tag/1.0.0-GA)

- First general-availability release of the reference examples: Docker Compose stacks, cloud deploy recipes (DigitalOcean, Fly.io, Railway, Render, Kubernetes), gateway YAML configs, getting-started samples (Python/Node.js), SDK integrations, streaming demos, and observability dashboards.

Full release history: [github.com/dvarahq/dvara-examples/releases](https://github.com/dvarahq/dvara-examples/releases).

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).

## License

MIT — see [LICENSE](LICENSE).
