/**
 * Streaming end-to-end demo — Node.js (OpenAI SDK).
 *
 * Three patterns:
 *   1. Basic streaming with content_filter handling.
 *   2. json_schema streaming — buffer-then-parse the accumulated content.
 *   3. Reconnect wrapper that retries on connection-class errors only.
 *
 * Usage:
 *   npm install
 *   export DVARA_URL="http://localhost:8080/v1"
 *   export DVARA_API_KEY="gw_<your-dvara-key>"
 *   npm start
 *
 * See https://dvarahq.com/docs/cookbook/streaming-end-to-end for the
 * narrated walkthrough.
 */

import OpenAI, { APIConnectionError } from "openai";

const client = new OpenAI({
  apiKey: process.env.DVARA_API_KEY ?? "your-dvara-api-key",
  baseURL: process.env.DVARA_URL ?? "http://localhost:8080/v1",
});

async function basicStreaming() {
  console.log("=== 1. Basic streaming ===");
  const stream = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [{ role: "user", content: "Write a haiku about programming." }],
    stream: true,
  });
  for await (const chunk of stream) {
    const delta = chunk.choices[0]?.delta;
    if (delta?.content) process.stdout.write(delta.content);
    if (chunk.choices[0]?.finish_reason === "content_filter") {
      process.stdout.write("\n[stream stopped: governance enforcement]");
    }
  }
  console.log("\n");
}

async function jsonSchemaStreaming() {
  console.log("=== 2. json_schema streaming ===");
  const schema = {
    type: "object",
    properties: {
      name: { type: "string" },
      age: { type: "integer" },
    },
    required: ["name", "age"],
  };
  const stream = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [{ role: "user", content: "Extract: John is 30 years old." }],
    response_format: {
      type: "json_schema",
      json_schema: { name: "person", schema, strict: true },
    },
    stream: true,
  });

  let buffer = "";
  for await (const chunk of stream) {
    const delta = chunk.choices[0]?.delta?.content;
    if (delta) buffer += delta;
  }
  const result = JSON.parse(buffer);
  console.log(`name: ${result.name}, age: ${result.age}\n`);
}

/**
 * Yield chunks with exponential-backoff retries on connection errors.
 * Does NOT retry on content_filter — that's an enforcement decision,
 * the same content would trigger the same rule on retry.
 */
async function* streamWithRetry(messages, attempts = 3, baseBackoff = 500) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      const stream = await client.chat.completions.create({
        model: "gpt-4o",
        messages,
        stream: true,
      });
      for await (const chunk of stream) yield chunk;
      return;
    } catch (err) {
      if (!(err instanceof APIConnectionError) || attempt === attempts - 1) throw err;
      await new Promise((resolve) => setTimeout(resolve, baseBackoff * 2 ** attempt));
    }
  }
}

async function reconnectDemo() {
  console.log("=== 3. Reconnect wrapper ===");
  for await (const chunk of streamWithRetry([
    { role: "user", content: "Say hello briefly." },
  ])) {
    const delta = chunk.choices[0]?.delta?.content;
    if (delta) process.stdout.write(delta);
  }
  console.log("\n");
}

await basicStreaming();
await jsonSchemaStreaming();
await reconnectDemo();
