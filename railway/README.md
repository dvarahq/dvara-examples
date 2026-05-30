# Railway

Deploy DVARA AI Gateway on [Railway](https://railway.com).

## What this template does

Single gateway-server service, built from the Dockerfile at the source repo's `gateway-server/Dockerfile`. Health probe targets `/actuator/health/readiness` (anonymous, rc24+ documented probe path). Auto-restart on failure up to 3 times.

## Setup

1. **Fork** [`dvarahq/dvara`](https://github.com/dvarahq/dvara) to your own GitHub account (Railway deploys from your fork).
2. Drop the `railway/railway.json` from this repo at the **root** of your fork (Railway reads it from there, not from a subdirectory).
3. In the Railway dashboard: **New Project → Deploy from GitHub repo** → select your fork.
4. **Set the four required Variables** before the first deploy or the gateway refuses startup:

| Variable | How to mint |
|---|---|
| `GATEWAY_LICENSE_KEY` | Production-signed `DVARA-…` envelope from your DVARA account team |
| `GATEWAY_ENCRYPTION_MASTER_PASSWORD` | `openssl rand -base64 32` — **escrow offline** (password manager + printed copy in a safe) |
| `GATEWAY_SERVER_API_KEY` | `openssl rand -base64 32` |
| `GATEWAY_METRICS_API_KEY` | `openssl rand -base64 32` — **must differ** from `GATEWAY_SERVER_API_KEY` (principle of least privilege) |

5. Optional: add a **Postgres plugin** (Railway dashboard → Add → Database → PostgreSQL). Set `SPRING_DATASOURCE_URL` from the plugin's connection-string variable. Without Postgres, the data plane runs but admin APIs that require persistence (tenants, audit) return errors.

6. Deploy. First deploy takes ~3–5 minutes (JVM warm-up).

## Verify

```bash
# Health (anonymous):
curl https://<your-service>.up.railway.app/actuator/health
# Expect: {"status":"UP"}

# Authenticated status (Bearer):
curl -H "Authorization: Bearer $GATEWAY_SERVER_API_KEY" \
     https://<your-service>.up.railway.app/actuator/gateway-status \
  | jq '{status, mode, license: .license.licensed}'
# Expect: status=running, mode=full, license=true
```

## Limitations of this template

- **No PG bundled** — Railway makes Postgres easy (one-click add-on) but the template doesn't auto-provision it. For a production install, add the PG plugin first.
- **No `flightdeck` Console** — the template deploys only `gateway-server`. To get the Console + tenant Portal, deploy a second service from `flightdeck/Dockerfile` and wire `GATEWAY_SERVER_URL` to the gateway-server's internal Railway URL.
- **`:latest` Docker tag is not used** — Railway builds from the Dockerfile directly. Pin via your fork's branch protection.

## Related

- **[`../digitalocean/`](../digitalocean/)** — DigitalOcean App Platform shapes (smoke + production with Managed PG addon)
- **[`../render/`](../render/)** — Render Blueprint
- **[`../flyio/`](../flyio/)** — Fly.io config
- **[`../kubernetes/`](../kubernetes/)** — Helm chart reference values for full multi-service deploys
