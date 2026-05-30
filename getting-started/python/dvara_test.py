"""Getting Started — send your first request through Dvara Gateway."""

import os
from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),  # Dvara API key — use a real gw_ key in production
    base_url="http://localhost:8080/v1"
)

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "What is the capital of India?"}]
)
print(response.choices[0].message.content)
