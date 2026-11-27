# Govern MCP in 5 minutes

One script starts DVARA, a demo MCP server and the configuration that governs it. Another sends
four tool calls through the gateway and shows the result of each one. No licence key, no provider
account and no clicking through the console.

```bash
git clone --depth 1 https://github.com/dvarahq/dvara-examples.git
cd dvara-examples/docker-compose/mcp-quickstart
./start.sh
./demo.sh
```

```text
1. tools/list: the agent sees the demo server's tools, namespaced by server
   demo-tools__delete_customer
   demo-tools__lookup_customer
   demo-tools__get_order_status

2. get_order_status: a harmless tool. Allowed, and recorded.
   Order A-1001: shipped, arriving Thursday.

3. lookup_customer: the server returns an email, a phone and a card number.
   The gateway redacts them before the agent sees the result:
   Customer C-42: Jane Doe, [REDACTED_EMAIL], [REDACTED_PHONE_NUMBER], card on file [REDACTED_CREDIT_CARD], plan Enterprise.

4. delete_customer: a policy denies it. The server is never called.
   REFUSED (MCP_POLICY_DENIED): delete_customer is irreversible and not allowed in this workspace
```

Every call is in Flightdeck's audit trail at <http://localhost:8090/mcp/tool-calls>. `start.sh`
prints the owner login.

## Prerequisites

- [Docker](https://docs.docker.com/get-started/get-docker/) with Compose v2
- [JBang](https://www.jbang.dev/download/), which runs the demo MCP server from a single Java file.
  It fetches a JDK by itself if you don't have Java 21 or later.
- `curl` and `openssl`
- Ports `8060`, `8080` and `8090` free

The DVARA images are `linux/amd64`. On Apple Silicon, Docker Desktop runs them under emulation,
so the first start takes longer.

## What `start.sh` does

1. **Writes `.env`** with a fresh secret for each of the six the stack needs, an API key, an
   owner password and a password for a second user. It runs only when `.env` doesn't exist yet.
2. **Starts the demo MCP server** (`DemoMcpServer.java`), a Spring AI MCP server on
   `http://localhost:8060/mcp` that speaks Streamable HTTP. It has three tools:
   `get_order_status`, `lookup_customer` (its result carries PII) and `delete_customer`
   (irreversible).
3. **Starts PostgreSQL, Flightdeck and the gateway** (`docker-compose.yml`, DVARA 1.17.0).
   Flightdeck reads `bootstrap.yaml` on first boot and creates the `quickstart` workspace. It also
   registers the gateway's cluster, `main`, and the gateway joins it.
4. **Creates the Flightdeck owner, signs in, gives the `main` environment to the workspace and
   creates its API key** (into `.env` as `DEMO_API_KEY`). A key works on a gateway only through an
   environment of the gateway's cluster, so the key is made after the environment. Then it
   **imports `seed-config.json`**, which contains:
   - the MCP server registration, pointing at the demo server on the host
   - a policy that denies `delete_customer`
   - `pii.action: REDACT` on the workspace
5. **Tests the policy, then activates it with a second person's approval.** An import saves a
   policy as a draft. Since 1.17.0 a policy version must be tested on a gateway before its
   activation can be requested, so `start.sh` runs one test on the policy's **Test** tab: a call
   to `delete_customer`, which the policy denies. Flightdeck makes a policy live only when
   someone other than the requester approves it. `start.sh` invites a policy admin
   (`approver@quickstart.local`), requests the activation as the owner and approves it as the
   policy admin.
6. **Syncs the server's tool catalog** and waits until the gateway lists the tools.

Run it again at any time. It skips what already exists and re-applies the configuration.

## Use your own MCP client

Point any client that speaks Streamable HTTP at the gateway:

| | |
|---|---|
| URL | `http://localhost:8080/mcp` |
| Header | `Authorization: Bearer <DEMO_API_KEY from .env>` |
| Tool names | `demo-tools__<tool>` |

To govern your own MCP server, add it in Flightdeck under **MCP → Servers** and select
**Sync Tools**. Its tools appear on the same endpoint as `<serverId>__<tool>`.

## Model calls

The MCP calls need no model. The gateway also answers `/v1` model calls with the same key, but
since 1.17.0 only for models its cluster lists, and this stack lists none: a model call is refused
with `model_not_on_cluster`. The mock provider is on, but in 1.17.0 a cluster cannot list a mock
model yet. To call a real model, add a provider key to `.env` (for example `OPENAI_API_KEY`) and
follow [Set up a model before the first call](../README.md#set-up-a-model-before-the-first-call-since-1170).

## Run the MCP server without the gateway

[`../mcpworks-standalone/`](../mcpworks-standalone/) runs an MCP server on the MCPWorks runtime
from one `server.yaml`, with no Flightdeck and no gateway.

## Licence

With no licence key every feature runs, MCP and A2A included, for non-production use within 3
workspaces and 100,000 governed calls a month. A licence removes those limits and grants
production rights and support. See [Licensing](https://dvarahq.com/docs/licensing).

## Stop

```bash
./stop.sh           # stop the stack and the MCP server, keep the data
./stop.sh --reset   # also delete the database, .env and the generated state
```
