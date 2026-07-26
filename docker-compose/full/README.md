# Full stack — all four Dvara planes

Every Dvara component running together: the LLM gateway, the DVARA Console (Flightdeck), the MCP
proxy for agent tool governance, and the A2A proxy for agent-to-agent governance, over one
PostgreSQL.

| Service | Port | Image |
|---|---|---|
| `postgres` | `25432` → 5432 | `postgres:16-alpine` |
| `dvara-gateway` | `8080` | `ghcr.io/dvarahq/dvara-llm-gateway-ee:1.6.0` |
| `dvara-flightdeck` | `8090` | `ghcr.io/dvarahq/dvara-flightdeck-ee:1.6.0` |
| `dvara-mcp-gateway` | `8070` | `ghcr.io/dvarahq/dvara-mcp-gateway:1.6.0` |
| `dvara-a2a-gateway` | `8075` | `ghcr.io/dvarahq/dvara-a2a-gateway:1.6.0` |

## ⚠️ Before you start — this stack is Enterprise only

**All four Dvara images above are private.** Unlike `quick-start/`, `multi-provider/`, `ollama/`,
and `with-email/` — which use the public `dvara-llm-gateway` / `dvara-flightdeck` images and run
the free Community Edition with a blank licence key — this stack needs **both**:

1. **Registry access.** `docker login ghcr.io` with an account entitled to the private packages.
   Without it `docker compose pull` fails on the first Dvara image with a denial that does not
   name the cause:

   ```
   Error response from daemon: denied
   ```

   That is an entitlement problem, not a typo in the tag.

2. **A valid licence.** The MCP and A2A planes are Enterprise-only and have **no Community
   tier** — they refuse to boot without a `DVARA-` envelope rather than degrading. A blank
   `DVARA_LICENSE_KEY` works for the other stacks; here it does not.

Don't have either? Use [`quick-start/`](../quick-start) — it runs the governed gateway and the
Console for free — and email [support@dvarahq.com](mailto:support@dvarahq.com) for a self-hosted
trial envelope and registry access. (The **Start Free Trial** button on dvarahq.com provisions a
hosted SaaS account; it does not issue a self-hosted envelope.)

## Quick start

```bash
docker login ghcr.io          # required — see above

cp .env.example .env
# Set DVARA_LICENSE_KEY to your DVARA- envelope.
# Mint each secret with: openssl rand -base64 32
#   DVARA_ENCRYPTION_MASTER_PASSWORD  — escrow this offline, losing it bricks
#                                       every ENCRYPTED provider credential
#   DVARA_AUDIT_HMAC_SECRET           — refused on production profiles if blank
#   DVARA_ACTUATOR_API_KEY            — these two must DIFFER from each other
#   DVARA_ACTUATOR_METRICS_API_KEY
# Set at least one provider key (OPENAI_API_KEY / ANTHROPIC_API_KEY / GEMINI_API_KEY).

docker compose up -d
docker compose ps             # all 5 services should be healthy
open http://localhost:8090    # first visit → /setup to create the owner account
```

## Verifying each plane

```bash
# LLM gateway
curl -s localhost:8080/actuator/health

# MCP proxy
curl -s localhost:8070/actuator/health

# A2A proxy
curl -s localhost:8075/actuator/health
```

All three should report `{"status":"UP"}`. The actuator health probes are deliberately anonymous
so they work without a Bearer token; anything else under `/actuator` needs
`DVARA_ACTUATOR_API_KEY`, and `/actuator/prometheus` needs the separate
`DVARA_ACTUATOR_METRICS_API_KEY` (they must differ — a leaked metrics token must not unlock the
rest).

Postgres is on host port **`25432`**, not 5432, so it won't collide with a local install.

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).
