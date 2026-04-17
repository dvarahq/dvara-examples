"""
Dvara Gateway — OpenAI Python SDK Integration

Usage:
    pip install openai
    export DVARA_URL="http://localhost:8080/v1"
    export DVARA_API_KEY="your-dvara-api-key"
    python openai_example.py
"""

import os

from openai import OpenAI

DVARA_URL = os.getenv("DVARA_URL", "http://localhost:8080/v1")
DVARA_API_KEY = os.getenv("DVARA_API_KEY", "your-dvara-api-key")

client = OpenAI(base_url=DVARA_URL, api_key=DVARA_API_KEY)


def chat_completion():
    """Basic chat completion."""
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "Explain quantum computing in one paragraph."},
        ],
        max_tokens=256,
    )
    print("=== Chat Completion ===")
    print(response.choices[0].message.content)
    print(f"Tokens: {response.usage.total_tokens}")
    print()


def streaming():
    """Streaming chat completion."""
    print("=== Streaming ===")
    stream = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": "Write a haiku about APIs."}],
        stream=True,
    )
    for chunk in stream:
        if chunk.choices[0].delta.content:
            print(chunk.choices[0].delta.content, end="")
    print("\n")


def structured_output():
    """Structured output with JSON schema."""
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": "List 3 planets with their diameter in km."}],
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "planets",
                "strict": True,
                "schema": {
                    "type": "object",
                    "properties": {
                        "planets": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "name": {"type": "string"},
                                    "diameter_km": {"type": "integer"},
                                },
                                "required": ["name", "diameter_km"],
                            },
                        }
                    },
                    "required": ["planets"],
                },
            },
        },
    )
    print("=== Structured Output ===")
    print(response.choices[0].message.content)
    print()


def tool_calls():
    """Function calling / tool use."""
    tools = [
        {
            "type": "function",
            "function": {
                "name": "get_weather",
                "description": "Get current weather for a city",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "city": {"type": "string", "description": "City name"},
                    },
                    "required": ["city"],
                },
            },
        }
    ]
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": "What's the weather in Tokyo?"}],
        tools=tools,
        tool_choice="auto",
    )
    print("=== Tool Calls ===")
    if response.choices[0].message.tool_calls:
        for tc in response.choices[0].message.tool_calls:
            print(f"Function: {tc.function.name}")
            print(f"Arguments: {tc.function.arguments}")
    print()


def embeddings():
    """Embeddings."""
    response = client.embeddings.create(
        model="text-embedding-3-small",
        input="The quick brown fox jumps over the lazy dog.",
    )
    print("=== Embeddings ===")
    print(f"Dimensions: {len(response.data[0].embedding)}")
    print(f"First 5 values: {response.data[0].embedding[:5]}")
    print()


def multi_model():
    """Use different models through the same gateway."""
    models = ["gpt-4o", "claude-sonnet-4-20250514", "gemini-2.0-flash"]
    print("=== Multi-Model ===")
    for model in models:
        try:
            response = client.chat.completions.create(
                model=model,
                messages=[{"role": "user", "content": "Say hello in one sentence."}],
                max_tokens=50,
            )
            print(f"{model}: {response.choices[0].message.content}")
        except Exception as e:
            print(f"{model}: Error — {e}")
    print()


if __name__ == "__main__":
    chat_completion()
    streaming()
    structured_output()
    tool_calls()
    embeddings()
    multi_model()
