"""
Streaming end-to-end demo — Python (OpenAI SDK).

Three patterns:
1. Basic streaming with content_filter handling.
2. json_schema streaming — buffer-then-parse the accumulated content.
3. Reconnect wrapper that retries on connection-class errors only.

Usage:
    pip install -r requirements.txt
    export DVARA_URL="http://localhost:8080/v1"
    export DVARA_API_KEY="gw_<your-dvara-key>"
    python streaming_demo.py

See https://dvarahq.com/docs/cookbook/streaming-end-to-end for the
narrated walkthrough.
"""

import json
import os
import time
from typing import Iterable

from openai import APIConnectionError, OpenAI

DVARA_URL = os.getenv("DVARA_URL", "http://localhost:8080/v1")
DVARA_API_KEY = os.getenv("DVARA_API_KEY", "your-dvara-api-key")

client = OpenAI(base_url=DVARA_URL, api_key=DVARA_API_KEY)


def basic_streaming() -> None:
    """Print each delta as it arrives. Treat content_filter as graceful end."""
    print("=== 1. Basic streaming ===")
    stream = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": "Write a haiku about programming."}],
        stream=True,
    )
    for chunk in stream:
        delta = chunk.choices[0].delta
        if delta.content:
            print(delta.content, end="", flush=True)
        if chunk.choices[0].finish_reason == "content_filter":
            print("\n[stream stopped: governance enforcement]", end="")
    print("\n")


def json_schema_streaming() -> None:
    """Stream a structured-output response and parse once complete.

    The gateway flattens any provider-side tool-use rewrite (Anthropic,
    Bedrock) into normal content deltas, so this code works no matter
    which provider the route picked.
    """
    print("=== 2. json_schema streaming ===")
    schema = {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "age": {"type": "integer"},
        },
        "required": ["name", "age"],
    }
    stream = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": "Extract: John is 30 years old."}],
        response_format={
            "type": "json_schema",
            "json_schema": {"name": "person", "schema": schema, "strict": True},
        },
        stream=True,
    )
    buffer: list[str] = []
    for chunk in stream:
        if chunk.choices[0].delta.content:
            buffer.append(chunk.choices[0].delta.content)

    result = json.loads("".join(buffer))
    print(f"name: {result['name']}, age: {result['age']}\n")


def stream_with_retry(messages, attempts: int = 3, base_backoff: float = 0.5) -> Iterable:
    """Yield chunks, retrying with exponential backoff on connection errors.

    Crucially, this wrapper only retries on transport-class failures.
    A finish_reason: content_filter is an enforcement decision — the
    same content would trigger the same rule on retry, so don't loop.
    """
    for attempt in range(attempts):
        try:
            for chunk in client.chat.completions.create(
                model="gpt-4o", messages=messages, stream=True,
            ):
                yield chunk
            return
        except APIConnectionError:
            if attempt == attempts - 1:
                raise
            time.sleep(base_backoff * (2 ** attempt))


def reconnect_demo() -> None:
    print("=== 3. Reconnect wrapper ===")
    for chunk in stream_with_retry([{"role": "user", "content": "Say hello briefly."}]):
        delta = chunk.choices[0].delta
        if delta.content:
            print(delta.content, end="", flush=True)
    print("\n")


if __name__ == "__main__":
    basic_streaming()
    json_schema_streaming()
    reconnect_demo()
