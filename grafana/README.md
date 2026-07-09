# Grafana + Prometheus monitoring stack

Pre-built Grafana dashboards and Prometheus alerting rules for DVARA AI Gateway. Spin up alongside the base DVARA Docker Compose stack with one extra `-f` flag.

## What's included

- **5 Grafana dashboards** (auto-provisioned on startup)

| Dashboard | UID | What it shows |
|---|---|---|
| Gateway Overview | `dvara-overview` | Request volume, P50/P95/P99 latency, error rates, provider health, token usage, cost |
| FinOps & Budget | `dvara-finops` | Cost by tenant/model/provider, budget enforcement, model downgrades, anomalies |
| MCP Proxy & Agentic | `dvara-mcp` | Tool calls, agent sessions, loop detection, approval gates, injection detection |
| Policy & Routing | `dvara-policy-routing` | Shadow policy divergence, canary testing, priority routing, config refresh |
| Infrastructure | `dvara-infrastructure` | Config poller health, cache cluster, rate-limit counters |

All dashboards include template variables for filtering by tenant, provider, model, and server.

- **12 Prometheus alert rules** (`alerts/dvara-alerts.yml`)

| Alert | Severity | Condition |
|---|---|---|
| `DvaraHighErrorRate` | critical | Error rate > 5% for 5 min |
| `DvaraHighP95Latency` | warning | P95 latency > 5s for 5 min |
| `DvaraProviderErrorSpike` | warning | Provider errors > 1/sec for 3 min |
| `DvaraCircuitBreakerOpen` | critical | Provider errors with zero successes for 2 min |
| `DvaraBudgetHardLimit` | critical | Hard budget cap hit |
| `DvaraBudgetSoftLimit` | warning | Soft budget threshold breached |
| `DvaraCostAnomaly` | warning | Cost rate exceeds baseline |
| `DvaraGuardrailBlocks` | warning | Guardrail blocks > 0.1/sec for 5 min |
| `DvaraAgentLoopDetected` | warning | Agent loop detected |
| `DvaraApprovalTimeouts` | warning | Approval gate timeouts |
| `DvaraMcpToolErrorRate` | warning | MCP server error rate > 10% for 5 min |
| `DvaraInjectionDetected` | critical | Prompt injection detected |

## Quick start

```bash
# 1. Capture your DVARA metrics Bearer in a host file (chmod 600):
echo -n "$DVARA_ACTUATOR_METRICS_API_KEY" > grafana/gateway-metrics-api-key
chmod 600 grafana/gateway-metrics-api-key

# 2. Start DVARA + monitoring overlay (from the dvara-examples root):
docker compose \
  -f docker-compose/full/docker-compose.yml \
  -f grafana/docker-compose.monitoring.yml \
  up -d

# 3. Open Grafana at http://localhost:3000 (admin / dvara) — dashboards
# are pre-provisioned. Open Prometheus at http://localhost:9090 to inspect
# scrape targets (all three should report "up").
```

## Why the Bearer token mount

DVARA rc24+ locks `/actuator/prometheus` behind `DVARA_ACTUATOR_METRICS_API_KEY` Bearer (per `dvarahq/dvara#665`). The `prometheus.yml` scrape jobs use `bearer_token_file` so Prometheus reads the token on every scrape — secret rotation is a file update with no restart. The compose file mounts a host file via the Docker Compose `secrets:` block.

For dev / smoke installs you can inline the token in `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: dvara-gateway
    metrics_path: /actuator/prometheus
    bearer_token: "<paste DVARA_ACTUATOR_METRICS_API_KEY here>"
    static_configs:
      - targets: ["gateway-server:8080"]
```

But never commit a real token to Git — keep the `bearer_token_file:` pattern in production.

## Customizing alerts

Edit `alerts/dvara-alerts.yml` to tune thresholds for your workload. `evaluation_interval: 15s` (set in `prometheus.yml`) controls how often Prometheus re-evaluates the rules.

Wire alerts to your notification backend (Slack, PagerDuty, OpsGenie, …) via Alertmanager — not included in this stack. The simplest pattern is to add an `alertmanager` service to the compose overlay with the standard alertmanager.yml routing.

## Related

- **[../datadog/](../datadog/)** — same metrics surface, Datadog Agent + monitor definitions instead. Pick one observability stack per environment.
- **[../kubernetes/](../kubernetes/)** — Helm chart reference values. Set `serviceMonitor.enabled: true` in your values file for Prometheus Operator scraping on Kubernetes instead of this compose overlay.
- **[../docker-compose/](../docker-compose/)** — base DVARA Compose stacks. This monitoring overlay is designed to compose with `docker-compose/full/`.
- **DVARA Prometheus metric reference** — see the [Observability docs](https://dvarahq.com/docs).
