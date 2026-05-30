/**
 * Dvara Gateway — Vercel AI SDK Integration
 *
 * Usage:
 *   npm install ai @ai-sdk/openai
 *   export DVARA_URL="http://localhost:8080/v1"
 *   export DVARA_API_KEY="your-dvara-api-key"
 *   npx tsx vercel_ai_example.ts
 */

import { createOpenAI } from "@ai-sdk/openai";
import { generateText, streamText } from "ai";

const DVARA_URL = process.env.DVARA_URL || "http://localhost:8080/v1";
const DVARA_API_KEY = process.env.DVARA_API_KEY || "your-dvara-api-key";

const dvara = createOpenAI({
  baseURL: DVARA_URL,
  apiKey: DVARA_API_KEY,
});

async function nonStreaming() {
  console.log("=== Non-Streaming ===");
  const { text, usage } = await generateText({
    model: dvara("gpt-4o"),
    prompt: "Explain REST APIs briefly.",
  });
  console.log(text);
  console.log(`Tokens: ${usage.totalTokens}`);
  console.log();
}

async function streaming() {
  console.log("=== Streaming ===");
  const result = streamText({
    model: dvara("gpt-4o"),
    prompt: "Write a short story about a robot.",
  });
  for await (const chunk of result.textStream) {
    process.stdout.write(chunk);
  }
  console.log("\n");
}

async function multiModel() {
  console.log("=== Multi-Model ===");
  const models = ["gpt-4o", "claude-sonnet-4-20250514", "gemini-2.0-flash"];
  for (const model of models) {
    try {
      const { text } = await generateText({
        model: dvara(model),
        prompt: "Say hello in one sentence.",
        maxTokens: 50,
      });
      console.log(`${model}: ${text}`);
    } catch (e: any) {
      console.log(`${model}: Error — ${e.message}`);
    }
  }
}

async function main() {
  await nonStreaming();
  await streaming();
  await multiModel();
}

main().catch(console.error);
