# Datadog integration

Datadog observability config for DVARA AI Gateway. Two files:

| File | Purpose |
|---|---|
| **[`conf.d/dvara.yaml`](conf.d/dvara.yaml)** | OpenMetrics integration — Datadog Agent scrapes `/actuator/prometheus` on every DVARA instance and forwards the metrics to Datadog under the `dvara.*` namespace. |
| **[`monitors.yaml`](monitors.yaml)** | Six pre-built Datadog monitors covering the load-bearing failure modes (error rate, P95 latency, budget breach, provider circuit, agent loop, injection attempt). |

## Install the OpenMetrics scrape

1. Copy `conf.d/dvara.yaml` to your Datadog Agent's `conf.d/openmetrics.d/` directory on every host running DVARA (or in your DaemonSet ConfigMap on Kubernetes).
2. **Set the Bearer auth secret.** On DVARA rc24+, `/actuator/prometheus` is locked down behind `DVARA_ACTUATOR_METRICS_API_KEY` (per `dvarahq/dvara#665`). The shipped config uses Datadog Agent's secret backend syntax:

   ```yaml
   headers:
     Authorization: "Bearer ENC[dvara_metrics_api_key]"
   ```

   Configure the `dvara_metrics_api_key` secret in your Agent's `secret_backend_command` (see [Datadog Secrets Management](https://docs.datadoghq.com/agent/configuration/secrets-management/)). For dev / local Agent installs, replace `ENC[…]` with the inline literal:

   ```yaml
   headers:
     Authorization: "Bearer <paste DVARA_ACTUATOR_METRICS_API_KEY here>"
   ```

3. Adjust the `openmetrics_endpoint:` hostnames. The example file uses `gateway-server` + `mcp-proxy-server` — Kubernetes service names if you're using the [`../kubernetes/`](../kubernetes/) charts. Replace with your actual hostnames.
4. Restart the Datadog Agent. Metrics should arrive within ~60s under the `dvara.*` namespace.

## Import the monitors

Via [datadog-ci](https://github.com/DataDog/datadog-ci):

```bash
DD_API_KEY=… DD_APP_KEY=… datadog-ci monitors sync --config monitors.yaml
```

Or manually copy each monitor definition into the Datadog UI (Monitors → New Monitor → Metric alert).

Adjust the `@slack-…` / `@pagerduty-…` handles in the `message:` blocks to your team's notification channels before importing — Datadog will fail to create monitors that reference handles that don't exist in your workspace.

## Metric coverage

Every metric in the OpenMetrics config is **verified against the source emit sites** as of DVARA rc28 (May 2026). All 30+ counters and histograms map to Micrometer registrations in:

- `gateway-*` modules → `GatewayMetrics` (`gateway_requests_total`, `gateway_latency_seconds`, `gateway_tokens_total`, …)
- `mcp-proxy-server/.../McpMetrics` → MCP-side counters
- `enterprise-*` modules → feature-specific counters (budget, guardrail, schema, agentic, MCP injection)

If a metric you expect isn't there, check the [Observability docs](https://dvarahq.com/docs) for the canonical list.

## Related

- **[../kubernetes/](../kubernetes/)** — Helm chart reference values. For Prometheus Operator scraping, set `serviceMonitor.enabled: true` in your values file; this Datadog integration is for environments that scrape DVARA directly via Datadog Agent instead of Prometheus + remote-write.
- **[../docker-compose/](../docker-compose/)** — local Docker Compose stacks (Datadog Agent runs as a sibling container).
- **DVARA docs** — [Observability stack](https://dvarahq.com/docs/governance/observability) on the website.
