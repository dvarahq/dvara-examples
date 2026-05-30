# Dvara Examples

Reference configurations, compose files, and SDK integration samples for the [Dvara LLM Gateway](https://dvarahq.com).

## Contents

| Directory | Description |
|---|---|
| **[docker-compose/](docker-compose/)** | Ready-to-run Docker Compose stacks (quick-start, multi-provider, full, ollama) |
| **[getting-started/](getting-started/)** | First-request scripts in Python and Node.js — basic chat, streaming, structured outputs, multi-provider |
| **[sdk-integrations/](sdk-integrations/)** | Framework examples — OpenAI SDK, LangChain, LiteLLM, Pydantic AI, Vercel AI, Spring AI, LangChain4j |

## Quick start

```bash
git clone https://github.com/dvarahq/dvara-examples.git
cd dvara-examples/docker-compose/quick-start
cp .env.example .env
# edit .env — set GATEWAY_LICENSE_KEY and OPENAI_API_KEY
docker compose up -d
```

Gateway ready at http://localhost:8080, DVARA Console at http://localhost:8090.

```bash
# Create a tenant and API key in the Console, then:
export DVARA_API_KEY="gw_<your-key>"

cd ../../getting-started/python
pip install -r requirements.txt
python dvara_test.py
```

## Documentation

Full product docs at [dvarahq.com/docs](https://dvarahq.com/docs).

## License

MIT — see [LICENSE](LICENSE).
