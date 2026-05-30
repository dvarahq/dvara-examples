# Fly.io

Deploy DVARA AI Gateway on [Fly.io](https://fly.io).

## What this template does

Single Fly Machine on shared-cpu / 1GB RAM in `iad` (Ashburn). Anonymous health probe at `/actuator/health/readiness`. Force HTTPS. Auto-stop machine when idle, auto-start on request. Concurrency soft-limit 200 / hard-limit 250.

## Setup

```bash
# 1. Mint the four required secrets:
export GATEWAY_LICENSE_KEY=DVARA-…                                    # production-signed envelope
export GATEWAY_ENCRYPTION_MASTER_PASSWORD=$(openssl rand -base64 32)  # ESCROW OFFLINE
export GATEWAY_SERVER_API_KEY=$(openssl rand -base64 32)
export GATEWAY_METRICS_API_KEY=$(openssl rand -base64 32)
[ "$GATEWAY_SERVER_API_KEY" != "$GATEWAY_METRICS_API_KEY" ] && echo OK || echo "MUST DIFFER — regenerate"

# 2. Launch (uses fly.toml in cwd; --copy-config keeps it):
fly launch --copy-config --no-deploy

# 3. Set the four secrets:
fly secrets set \
  GATEWAY_LICENSE_KEY="$GATEWAY_LICENSE_KEY" \
  GATEWAY_ENCRYPTION_MASTER_PASSWORD="$GATEWAY_ENCRYPTION_MASTER_PASSWORD" \
  GATEWAY_SERVER_API_KEY="$GATEWAY_SERVER_API_KEY" \
  GATEWAY_METRICS_API_KEY="$GATEWAY_METRICS_API_KEY"

# 4. Optional — provision Fly Managed Postgres:
fly postgres create
fly postgres attach <db-name>
# Fly auto-injects DATABASE_URL (postgres://... format). Convert to JDBC:
fly secrets set SPRING_DATASOURCE_URL="jdbc:postgresql://..."

# 5. Deploy:
fly deploy
```

## Verify

```bash
# Anonymous:
curl https://dvara-gateway.fly.dev/actuator/health
# Expect: {"status":"UP"}

# Authenticated:
curl -H "Authorization: Bearer $GATEWAY_SERVER_API_KEY" \
     https://dvara-gateway.fly.dev/actuator/gateway-status \
  | jq '{status, mode, license: .license.licensed}'
```

## Operational notes

- **`min_machines_running = 0`** — machines auto-stop after inactivity. First request after idle wakes the machine in ~5s. Bump to `1` for sub-second latency on always-warm deploys.
- **`primary_region = "iad"`** — change to a region closer to your upstream providers (`sea`, `fra`, `nrt`, etc.).
- **`memory = "1gb"`** — sized for smoke / light load. Increase to `2gb` or `4gb` for production; bump `JAVA_OPTS` `-Xmx` accordingly (rule of thumb: heap ≤ 75% of container memory).
- **Multi-region** — `fly scale count <n> --region <region>` to deploy additional machines globally. Fly handles geo-routing automatically.

## Limitations

- **No flightdeck Console** — single-service template. Add a second app for `flightdeck/Dockerfile`, attach the same Postgres, set `GATEWAY_SERVER_URL` to the gateway's `<app>.flycast` internal address.
- **Bring-your-own Postgres** — Fly's Managed Postgres is the easiest path (`fly postgres create`); external managed providers (Crunchy, Supabase, etc.) work too via `SPRING_DATASOURCE_URL`.

## Related

- **[`../digitalocean/`](../digitalocean/)** — App Platform shapes with Managed PG addon
- **[`../railway/`](../railway/)** — Railway template
- **[`../render/`](../render/)** — Render Blueprint
- **[`../kubernetes/`](../kubernetes/)** — Helm chart for full multi-service deploys
