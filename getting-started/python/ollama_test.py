"""Local models — use Ollama through Dvara (no provider key needed; a Dvara API key is).

Since 1.17.0 the model must be on the gateway's cluster: add the ollama provider, release
llama3.2 on its Models page and add it on the cluster's page in Flightdeck. Callers send the
model's own name, llama3.2, not ollama/llama3.2.
"""
import os

from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),
    base_url="http://localhost:8080/v1"
)

response = client.chat.completions.create(
    model="llama3.2",
    messages=[{"role": "user", "content": "What is the capital of India?"}]
)
print(response.choices[0].message.content)
