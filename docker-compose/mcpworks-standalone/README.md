# MCPWorks standalone: one MCP server from one file

The MCPWorks runtime serves an MCP server that you describe in one file, `server.yaml`. This
example runs it on its own: no Flightdeck, no gateway, no database and no signed bundle. One tool,
`get_order_status`, calls a small HTTP API that the stack also starts.

```bash
cd dvara-examples/docker-compose/mcpworks-standalone
echo "MCP_CALLER_KEY=$(openssl rand -hex 24)" > .env
docker compose up -d
./call.sh
```

```text
Tools:
  get_order_status
get_order_status A-1001:
  {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"id\":\"A-1001\",\"status\":\"shipped\",\"arriving\":\"Thursday\"}"}],"isError":false,"structuredContent":{"id":"A-1001","status":"shipped","arriving":"Thursday"}}}
```

## Prerequisites

- Docker with Compose v2, `curl` and `openssl`
- Port `8095` free (set `MCP_PORT` in `.env` to use another)
- Pull access to `ghcr.io/dvarahq/mcp-run:1.17.1`. The image is not public yet: run
  `docker login ghcr.io` first with an account that can read it.

The image is `linux/amd64`. On Apple silicon, Docker Desktop runs it under emulation.

## What runs

| Service | Image | What it does |
|---|---|---|
| `mcp-run` | `ghcr.io/dvarahq/mcp-run:1.17.1` | The MCPWorks runtime. Serves `config/server.yaml` at `http://localhost:8095/mcp` (Streamable HTTP). |
| `orders-api` | `nginx:1.29-alpine` | Stands in for the API the tool calls. It serves `upstream/orders/A-1001.json`. |

The runtime is set up by four environment variables in `docker-compose.yml`:

| Variable | Value | Why |
|---|---|---|
| `DVARA_MCP_SERVER_FILE` | `/etc/dvara/server.yaml` | Serve this file, not a bundle. |
| `DVARA_MCP_BUNDLE_TRUST_MODE` | `file-unsigned` | Required. Says you accept a server with no signature. The runtime logs `UNSIGNED: serving the server.yaml …` at start. |
| `DVARA_MCP_INGRESS_MODE` | `api-key` | Lets in the callers the file lists, each with its own key. |
| `DVARA_MCP_LOCAL_STORE_PATH` | `/var/lib/dvara-mcp/local-store.db` | The runtime's local file, on the `mcp-state` volume. |

The runtime is ready when `GET http://localhost:8095/actuator/health/readiness` answers `200`.

## Who can call

Standalone, the runtime has no owner and no grants. It lets in every caller listed under
`spec.callers` in `server.yaml`, with that caller's key as a bearer token:

```
Authorization: Bearer <MCP_CALLER_KEY from .env>
```

A request with no key, or a key that is not a caller's, gets `401`. A key must be at least 32
characters. The caller here, `demo-agent`, has the scope `orders.read`. A `GET` tool is read-only
and needs `<server>.read`, so `demo-agent` can call `get_order_status`.

## Change the server

Edit `config/server.yaml` and save. The runtime checks the file every second and loads an edit
once it reads the same at two checks in a row. A wrong edit is refused with the line of each
problem, and the previous version keeps serving: see `docker compose logs mcp-run`.

The folder is mounted, not the file: editors save by writing a new file, and a mount of one file
keeps seeing the old one.

To call your own API, change the tool's `http.url`. A tool can also run one SQL statement
(`jdbc:`) or read and write objects in an S3 bucket (`s3:`). For an upstream key, add a
`credentials:` entry that names an environment variable, never the value.

## Known issue in 1.17.1

The runtime answers each request as a server-sent event and closes the stream without its last
chunk. The answer is complete, but `curl` may exit with code `18`, and the runtime logs an
`AuthorizationDeniedException` stack trace after each answer. `call.sh` allows for this.

## What this mode does not do

- Sign the server or promote versions. Connect the runtime to Flightdeck for that.
- Hold calls for an approver or count usage. A tool with `approval: approver` is refused.
- Serve resources or prompts.

## Stop

```bash
docker compose down        # stop, keep the runtime's local file
docker compose down -v     # also delete the mcp-state volume
```
