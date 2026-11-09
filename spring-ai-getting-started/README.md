# Spring AI getting started

A Spring Boot app that sends its Spring AI chat calls through the open-source DVARA LLM Gateway. The Java code is plain Spring AI: two properties in `application.yml` (the base URL and the API key) point it at DVARA, and every call then gets the workspace's policy and PII checks and a signed audit record.

It runs with the gateway's bundled Mock provider, so you need no provider account. The Mock provider here answers with the text it received, which lets you see what a model would have been sent.

Tested with Spring AI `2.0.2`, Spring Boot `4.1.2`, Java 25 and `ghcr.io/dvarahq/dvara-gateway-oss:1.13.0`.

## What is in this folder

| File | What it does |
|---|---|
| `docker-compose.yml` | Starts the open-source gateway on port 8080 with `gateway.yaml` and a file audit log |
| `gateway.yaml` | One workspace with PII redaction on, a route to the Mock provider, one API key, one deny policy |
| `.env.example` | The key fingerprint, the audit signing secret and the host port |
| `pom.xml` | Spring Boot 4.1 and `spring-ai-starter-model-openai`, versions from the Spring AI BOM |
| `src/main/resources/application.yml` | Points Spring AI's OpenAI client at DVARA |
| `src/main/java/.../SpringAiDvaraApplication.java` | Sends one prompt with `ChatClient` and prints the reply |

## Prerequisites

- Docker with Compose
- JDK 25 (the Maven wrapper downloads Maven)
- Port 8080 free, or set `DVARA_PORT` in `.env`

## 1. Generate a DVARA API key

```bash
docker run --rm ghcr.io/dvarahq/dvara-gateway-oss:1.13.0 \
  --generate-key --name spring-ai-demo --workspace default
```

It prints a `gw_...` key and a `key_hash`. The key is shown once.

## 2. Fill in `.env` and start the gateway

```bash
cp .env.example .env
# DVARA_KEY_HASH=sha256:...            the key_hash from step 1
# DVARA_AUDIT_HMAC_SECRET=...          openssl rand -base64 32
docker compose up -d
curl http://localhost:8080/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

## 3. Run the Spring AI app

```bash
export DVARA_API_KEY='gw_<the-key-printed-in-step-1>'
./mvnw spring-boot:run
```

```text
Prompt:   Email jane.doe@example.com the renewal quote for policy 4471.
Model:    mock/test-model
Reply:    The model received: Email [REDACTED_EMAIL] the renewal quote for policy 4471.
Response: mock-95260026a10848659bbac368d5d991a5
```

DVARA replaced the email address before the request reached the provider, because `gateway.yaml` sets `pii.action: REDACT` for the workspace. Without that line the default is `LOG`: the email is detected and recorded, and the request goes through unchanged.

## 4. Read the audit record

```bash
docker compose exec dvara-gateway cat /tmp/audit.log
```

```json
{"seq":1,"eventId":"0S674JF0850YP","timestamp":"2026-11-09T20:21:37.666851Z","workspaceId":"default","eventType":"PII_REDACTED","payload":{"entity_count":1,"entity_type_counts":{"EMAIL":1},"entity_types":"EMAIL","source":"request"},"previousHash":null,"hmac":"bcc77cfcf30fae82e509dce7cb10334e736401057f80dcd280582752b7902d83","signedAt":"2026-11-09T20:21:37.697505Z"}
{"seq":2,"eventId":"0S674JGCM50ZS","timestamp":"2026-11-09T20:21:38.021658Z","workspaceId":"default","eventType":"GATEWAY_RESPONSE","payload":{"api_key":"spring-ai-demo","cache_status":"MISS","doorway":"chat_completions","latency_ms":845,"model":"mock/test-model","policy_decision":"ALLOWED","provider":"mock","status":200,"tokens_total":"33","trace_id":"dc3cc3510c824700ab2aa28b7d62e725"},"previousHash":"bcc77cfcf30fae82e509dce7cb10334e736401057f80dcd280582752b7902d83","hmac":"d00de2be69564b0fee0c319a8452927627bb79de70f6e3062d3f41a29ba42296","signedAt":"2026-11-09T20:21:38.023017Z"}
```

The `PII_REDACTED` record names the entity type, not the value. Neither record holds the prompt or the reply. Each record carries the previous record's hash and its own HMAC.

## 5. See a policy refusal

`gateway.yaml` denies the model `mock/unapproved`. Ask for it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.ai.openai.chat.model=mock/unapproved
```

```text
com.openai.errors.PermissionDeniedException: 403: mock/unapproved is not an approved model
```

No provider is called. The audit log gains a `POLICY_DENIED` record naming the policy and the rule:

```json
{"seq":3,"eventId":"0S674MER4512N","timestamp":"2026-11-09T20:21:53.985233Z","workspaceId":"default","eventType":"POLICY_DENIED","payload":{"api_key":"spring-ai-demo","model":"mock/unapproved","policy_id":"approved-models-only","reason":"mock/unapproved is not an approved model","rule_id":"deny-unapproved","status":403,"user_id":null,"workspace_id":"default"},"previousHash":"d00de2be69564b0fee0c319a8452927627bb79de70f6e3062d3f41a29ba42296","hmac":"f8c077aefe524b7aaa131a3cdc6a4ca1b9448077ff32a9f77c0d1ee5549f7dd4","signedAt":"2026-11-09T20:21:53.986459Z"}
```

## 6. Verify the audit chain

```bash
docker compose exec dvara-gateway java -jar /app/app.jar --verify-audit /tmp/audit.log
```

```text
/tmp/audit.log: valid: 4 line(s), chain unbroken
```

An edited, removed or reordered record breaks the chain. The check uses the same `DVARA_AUDIT_HMAC_SECRET` that signed it.

## Troubleshooting

| What you see | Why | Fix |
|---|---|---|
| `NotFoundException: 404: Not found` | `base-url` has no `/v1`. Spring AI 2.0 appends `/chat/completions` to it | `http://localhost:8080/v1` |
| `UnauthorizedException: 401: Invalid API key.` | The key does not match a `key_hash` in `gateway.yaml`, or it is an OpenAI key | Export the `gw_...` key from step 1 |
| `BadRequestException: 400: No provider configured for model: gpt-4o` | No route matches the model name | Use a `mock...` model, or add a provider and route |

## Use a real provider

Replace the Mock provider and route in `gateway.yaml`, pass the provider key to the container, and remove the `DVARA_LLM_GATEWAY_PROVIDERS_MOCK_RESPONSE` line from `docker-compose.yml`:

```yaml
providers:
  - type: openai
    api_key: ${OPENAI_API_KEY}

routes:
  - id: openai-models
    model: "gpt*"
    provider: openai
```

Then set `spring.ai.openai.chat.model` to a model the route serves. The Spring AI code does not change.

## Clean up

```bash
docker compose down
```

The audit log lives inside the container and is removed with it. Mount a volume at its path to keep it.

## Learn more

- [Run DVARA Open Source](https://dvarahq.com/docs/open-source-quickstart)
- [Use DVARA from Java](https://dvarahq.com/docs/integrations/java)
- [Open-source gateway configuration](https://dvarahq.com/docs/open-source/configuration)
