"""Multi-provider — send the same prompt to OpenAI and Anthropic."""
import os

from openai import OpenAI

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),
    base_url="http://localhost:8080/v1"
)

# Routes to OpenAI
print("=== OpenAI (gpt-4o) ===")
openai_response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Explain virtual threads in Java in 2 sentences"}]
)
print(openai_response.choices[0].message.content)

# Routes to Anthropic — same client, same endpoint, same format
print("\n=== Anthropic (claude-sonnet-4-5) ===")
anthropic_response = client.chat.completions.create(
    model="claude-sonnet-4-5",
    messages=[{"role": "user", "content": "Explain virtual threads in Java in 2 sentences"}]
)
print(anthropic_response.choices[0].message.content)
