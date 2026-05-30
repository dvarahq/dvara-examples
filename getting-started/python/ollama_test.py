"""Local models — use Ollama through Dvara (no API key needed)."""
import os

from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),
    base_url="http://localhost:8080/v1"
)

response = client.chat.completions.create(
    model="ollama/llama3.2",
    messages=[{"role": "user", "content": "What is the capital of India?"}]
)
print(response.choices[0].message.content)
