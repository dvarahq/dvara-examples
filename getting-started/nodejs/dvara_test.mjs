// Getting Started — send your first request through Dvara Gateway.

import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.DVARA_API_KEY || "any-key",
  baseURL: "http://localhost:8080/v1",
});

const response = await client.chat.completions.create({
  model: "gpt-4o",
  messages: [{ role: "user", content: "What is the capital of India?" }],
});
console.log(response.choices[0].message.content);
