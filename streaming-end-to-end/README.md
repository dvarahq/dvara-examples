# Streaming end-to-end

Runnable SSE consumers for Dvara's `/v1/chat/completions` streaming endpoint, in three languages. Each demo exercises:

1. **Basic streaming** — standard delta consumption with `finish_reason: content_filter` handling.
2. **`json_schema` streaming** — buffer-then-parse strategy for structured-output streams (works regardless of which provider the gateway routed to).
3. **Reconnect on idle disconnects** — exponential-backoff wrapper with the explicit caveat that `content_filter` is **not** a retry signal.

For background — protocol shape, governance behaviour, limitations — read [Streaming](https://dvarahq.com/docs/streaming) first. The companion cookbook recipe at [Stream chat completions end-to-end](https://dvarahq.com/docs/cookbook/streaming-end-to-end) walks through the same code with annotations.

## Prerequisites

A running Dvara Gateway with at least one provider configured. The fastest path is the [quick-start Docker stack](../docker-compose/quick-start/):

```bash
cd ../docker-compose/quick-start
cp .env.example .env
# edit .env to set GATEWAY_LICENSE_KEY and OPENAI_API_KEY
docker compose up -d
```

Then create a tenant + API key from the DVARA Console at `http://localhost:8090` and export both:

```bash
export DVARA_URL="http://localhost:8080/v1"
export DVARA_API_KEY="gw_<your-dvara-key>"
```

## Python

```bash
cd python
pip install -r requirements.txt
python streaming_demo.py
```

Uses the OpenAI Python SDK; no manual SSE parsing.

## Node.js

```bash
cd nodejs
npm install
npm start
```

Uses the OpenAI JS SDK; same surface as the Python demo.

## Java

```bash
cd java
jbang StreamingDemo.java
```

Uses the JDK's standard-library `HttpClient` with line-iterator parsing. No build tool required — [JBang](https://www.jbang.dev/) handles dependencies (Jackson) and execution from the single source file.

## What success looks like

Each demo prints the haiku from the basic stream as it arrives, then the parsed JSON object from the `json_schema` stream, then exits cleanly. Output across the three languages is functionally identical because Dvara emits the same OpenAI-format chunks regardless of consumer language.

If a demo terminates early with `[stream stopped: governance enforcement]`, your Dvara instance has a PII or guardrail rule that fires on the test prompt — that's expected behaviour, not an error. Adjust the prompt or the rule's action.
