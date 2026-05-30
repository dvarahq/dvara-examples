"""Streaming — receive tokens as they are generated."""
import os

from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),
    base_url="http://localhost:8080/v1"
)

stream = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Write a haiku about gateways"}],
    stream=True,
)

for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")
print()  # newline at the end
