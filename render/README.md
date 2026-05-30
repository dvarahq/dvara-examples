# Render

Deploy DVARA AI Gateway on [Render](https://render.com) via the Blueprint format.

## What this template does

Single `web` service built from the Dockerfile at `gateway-server/Dockerfile` in the DVARA source repo. Anonymous health probe at `/actuator/health/readiness`. Four required SECRETs prompted at deploy time via Render's secret UI (`sync: false`).

## Setup

1. **Fork** [`dvarahq/dvara`](https://github.com/dvarahq/dvara) so Render can build from your fork.
2. Drop `render/render.yaml` from this repo into the **root** of your fork (Render's Blueprint loader expects it at the repo root).
3. In the Render dashboard: **New → Blueprint** → connect your fork.
4. Render prompts for the four SECRETs on first apply:

| Variable | How to mint |
|---|---|
| `GATEWAY_LICENSE_KEY` | Production-signed `DVARA-…` envelope from your DVARA account team |
| `GATEWAY_ENCRYPTION_MASTER_PASSWORD` | `openssl rand -base64 32` — **escrow offline** |
| `GATEWAY_SERVER_API_KEY` | `openssl rand -base64 32` |
| `GATEWAY_METRICS_API_KEY` | `openssl rand -base64 32` — must differ from `GATEWAY_SERVER_API_KEY` |

5. Optional: add a **Render Postgres** instance separately (Render dashboard → New → PostgreSQL). Set `SPRING_DATASOURCE_URL` from the instance's external connection string. Render Blueprint v1 doesn't auto-provision databases.

6. Apply. First boot ~3–5 minutes.

## Verify

```bash
curl https://<your-service>.onrender.com/actuator/health
# Expect: {"status":"UP"}

curl -H "Authorization: Bearer $GATEWAY_SERVER_API_KEY" \
     https://<your-service>.onrender.com/actuator/gateway-status \
  | jq '{status, mode, license: .license.licensed}'
```

## Limitations

- **No flightdeck Console** — single-service template. Add a second `web` service from `flightdeck/Dockerfile` and wire `GATEWAY_SERVER_URL` to the gateway service's internal URL.
- **`starter` plan is sized for smoke deploys.** Bump to `standard` or `pro` for production traffic.
- **No bundled PG** — Render's Postgres is a separate add-on; provision it before first deploy.

## Related

- **[`../digitalocean/`](../digitalocean/)** — App Platform shapes with Managed PG addon
- **[`../railway/`](../railway/)** — Railway template
- **[`../flyio/`](../flyio/)** — Fly.io config
- **[`../kubernetes/`](../kubernetes/)** — Helm chart for full multi-service deploys
