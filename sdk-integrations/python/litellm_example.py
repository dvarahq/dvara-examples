"""
Dvara Gateway — LiteLLM Integration

Usage:
    pip install litellm
    export DVARA_URL="http://localhost:8080/v1"
    export DVARA_API_KEY="your-dvara-api-key"
    python litellm_example.py
"""

import os

import litellm

DVARA_URL = os.getenv("DVARA_URL", "http://localhost:8080/v1")
DVARA_API_KEY = os.getenv("DVARA_API_KEY", "your-dvara-api-key")


def basic_chat():
    """Basic completion through Dvara."""
    print("=== Basic Chat ===")
    response = litellm.completion(
        model="openai/gpt-4o",
        api_base=DVARA_URL,
        api_key=DVARA_API_KEY,
        messages=[{"role": "user", "content": "Hello via LiteLLM!"}],
    )
    print(response.choices[0].message.content)
    print()


def multi_model():
    """Multiple models through the same gateway."""
    print("=== Multi-Model ===")
    models = ["openai/gpt-4o", "openai/claude-sonnet-4-20250514", "openai/gemini-2.0-flash"]
    for model in models:
        try:
            response = litellm.completion(
                model=model,
                api_base=DVARA_URL,
                api_key=DVARA_API_KEY,
                messages=[{"role": "user", "content": "Say hello in one sentence."}],
                max_tokens=50,
            )
            print(f"{model}: {response.choices[0].message.content}")
        except Exception as e:
            print(f"{model}: Error — {e}")
    print()


def streaming():
    """Streaming completion."""
    print("=== Streaming ===")
    response = litellm.completion(
        model="openai/gpt-4o",
        api_base=DVARA_URL,
        api_key=DVARA_API_KEY,
        messages=[{"role": "user", "content": "Write a haiku about code."}],
        stream=True,
    )
    for chunk in response:
        content = chunk.choices[0].delta.content
        if content:
            print(content, end="")
    print("\n")


if __name__ == "__main__":
    basic_chat()
    multi_model()
    streaming()
