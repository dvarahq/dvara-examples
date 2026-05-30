/**
 * Dvara Gateway — OpenAI JavaScript/TypeScript SDK Integration
 *
 * Usage:
 *   npm install openai
 *   export DVARA_URL="http://localhost:8080/v1"
 *   export DVARA_API_KEY="your-dvara-api-key"
 *   npx tsx openai_example.ts
 */

import OpenAI from "openai";

const DVARA_URL = process.env.DVARA_URL || "http://localhost:8080/v1";
const DVARA_API_KEY = process.env.DVARA_API_KEY || "your-dvara-api-key";

const client = new OpenAI({
  baseURL: DVARA_URL,
  apiKey: DVARA_API_KEY,
});

async function chatCompletion() {
  console.log("=== Chat Completion ===");
  const response = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [
      { role: "system", content: "You are a helpful assistant." },
      { role: "user", content: "Explain microservices in one paragraph." },
    ],
  });
  console.log(response.choices[0].message.content);
  console.log(`Tokens: ${response.usage?.total_tokens}`);
  console.log();
}

async function streaming() {
  console.log("=== Streaming ===");
  const stream = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [{ role: "user", content: "Write a poem about code." }],
    stream: true,
  });
  for await (const chunk of stream) {
    process.stdout.write(chunk.choices[0]?.delta?.content || "");
  }
  console.log("\n");
}

async function toolCalls() {
  console.log("=== Tool Calls ===");
  const response = await client.chat.completions.create({
    model: "gpt-4o",
    messages: [{ role: "user", content: "What's the weather in London?" }],
    tools: [
      {
        type: "function",
        function: {
          name: "get_weather",
          description: "Get weather for a city",
          parameters: {
            type: "object",
            properties: { city: { type: "string" } },
            required: ["city"],
          },
        },
      },
    ],
  });
  const toolCall = response.choices[0].message.tool_calls?.[0];
  if (toolCall) {
    console.log(`Function: ${toolCall.function.name}`);
    console.log(`Arguments: ${toolCall.function.arguments}`);
  }
  console.log();
}

async function multiModel() {
  console.log("=== Multi-Model ===");
  const models = ["gpt-4o", "claude-sonnet-4-20250514", "gemini-2.0-flash"];
  for (const model of models) {
    try {
      const response = await client.chat.completions.create({
        model,
        messages: [{ role: "user", content: "Say hello in one sentence." }],
        max_tokens: 50,
      });
      console.log(`${model}: ${response.choices[0].message.content}`);
    } catch (e: any) {
      console.log(`${model}: Error — ${e.message}`);
    }
  }
  console.log();
}

async function main() {
  await chatCompletion();
  await streaming();
  await toolCalls();
  await multiModel();
}

main().catch(console.error);
