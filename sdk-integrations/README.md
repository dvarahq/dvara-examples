# SDK Integration Examples

Working code examples for integrating Dvara Gateway with popular SDKs and frameworks.

## Prerequisites

1. **Dvara Gateway running** — follow the [Quick Start](../docker-compose/quick-start/) to get the stack up
2. **A tenant and API key** — create one from the DVARA Console at `http://localhost:8090`
3. **Target provider(s) configured** (e.g. `OPENAI_API_KEY` in your `.env`)

## Setup

```bash
export DVARA_URL="http://localhost:8080/v1"
export DVARA_API_KEY="gw_<your-dvara-key>"
```

## Python Examples

```bash
cd python
pip install -r requirements.txt

python openai_example.py        # OpenAI SDK — chat, streaming, structured output, tools, embeddings
python langchain_example.py     # LangChain — chains, streaming
python pydantic_ai_example.py   # Pydantic AI — structured output, tools
python litellm_example.py       # LiteLLM — multi-model, streaming
```

## TypeScript Examples

```bash
cd typescript
npm install

npm run openai        # OpenAI SDK — chat, streaming, tools, multi-model
npm run vercel-ai     # Vercel AI SDK — generateText, streamText
```

## Java Examples

### LangChain4j (with JBang)
```bash
cd java
jbang LangChain4jExample.java
```

### Spring AI
Copy `SpringAiExample.java` and `application.yml` into your Spring Boot project.

## Full Documentation

See [SDK & Framework Integrations](https://dvarahq.com/docs/sdk-integrations) for comprehensive documentation covering all supported SDKs.