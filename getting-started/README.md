# Getting Started Examples

Simple runnable scripts that demonstrate Dvara Gateway's OpenAI-compatible API. Each script is self-contained — just set your API key and run.

## Prerequisites

1. **Dvara Gateway running** — follow the [Quick Start](../docker-compose/quick-start/) to get the stack up
2. **A workspace and API key** — create them in the DVARA Console at `http://localhost:8090`
3. **At least one provider configured** (e.g. `OPENAI_API_KEY` in your `.env`)
4. **The models the scripts call, on the gateway's cluster** — since 1.17.0 a gateway serves only
   the models its cluster lists, and a new cluster lists none. Add `gpt-4o` (and
   `claude-sonnet-4-5` for `multi_provider.py`) as in
   [Set up a model before the first call](../docker-compose/README.md#set-up-a-model-before-the-first-call-since-1170).
   Until then each call is refused with `model_not_on_cluster`.

## Setup

Set your Dvara API key as an environment variable (or edit the scripts directly):

```bash
export DVARA_API_KEY="gw_<your-dvara-key>"
```

## Python

```bash
cd python
pip install -r requirements.txt

# First request — basic chat completion
python dvara_test.py

# Multi-provider — same client, OpenAI + Anthropic
python multi_provider.py

# Streaming — receive tokens as they arrive
python stream_test.py

# Structured outputs — JSON schema response format
python structured_test.py

# Local models — Ollama through Dvara (requires the ollama stack)
python ollama_test.py
```

## Node.js

```bash
cd nodejs
npm install

# First request — basic chat completion
node dvara_test.mjs
```

## What's next

- [SDK Integration Examples](../sdk-integrations/) — LangChain, LiteLLM, Pydantic AI, Vercel AI, Spring AI, LangChain4j
- [Dvara Documentation](https://dvarahq.com/docs/quickstart) — full quickstart guide
- [SDK & Framework Integrations](https://dvarahq.com/docs/sdk-integrations) — comprehensive SDK docs
