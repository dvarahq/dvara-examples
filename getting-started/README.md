# Getting Started Examples

Simple runnable scripts that demonstrate Dvara Gateway's OpenAI-compatible API. Each script is self-contained — just set your API key and run.

## Prerequisites

1. **Dvara Gateway running** — follow the [Quick Start](../docker-compose/quick-start/) to get the stack up
2. **A tenant and API key** — create one from the DVARA Console at `http://localhost:8090`
3. **At least one provider configured** (e.g. `OPENAI_API_KEY` in your `.env`)

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
