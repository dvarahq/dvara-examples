"""
Dvara Gateway — Pydantic AI Integration

Usage:
    pip install pydantic-ai
    export DVARA_URL="http://localhost:8080/v1"
    export DVARA_API_KEY="your-dvara-api-key"
    python pydantic_ai_example.py
"""

import os

from pydantic import BaseModel
from pydantic_ai import Agent
from pydantic_ai.models.openai import OpenAIModel

DVARA_URL = os.getenv("DVARA_URL", "http://localhost:8080/v1")
DVARA_API_KEY = os.getenv("DVARA_API_KEY", "your-dvara-api-key")

model = OpenAIModel(
    "gpt-4o",
    base_url=DVARA_URL,
    api_key=DVARA_API_KEY,
)


def basic_chat():
    """Basic agent invocation."""
    print("=== Basic Chat ===")
    agent = Agent(model)
    result = agent.run_sync("What is the capital of Japan?")
    print(result.data)
    print()


def structured_output():
    """Structured output with Pydantic models."""

    class CityInfo(BaseModel):
        name: str
        country: str
        population: int
        famous_for: list[str]

    print("=== Structured Output ===")
    agent = Agent(model, result_type=CityInfo)
    result = agent.run_sync("Tell me about Tokyo")
    city = result.data
    print(f"{city.name}, {city.country} — pop: {city.population:,}")
    print(f"Famous for: {', '.join(city.famous_for)}")
    print()


def tool_use():
    """Agent with tools."""
    print("=== Tool Use ===")
    agent = Agent(model, system_prompt="You help with math.")

    @agent.tool_plain
    def multiply(a: int, b: int) -> int:
        """Multiply two numbers."""
        return a * b

    result = agent.run_sync("What is 123 times 456?")
    print(result.data)
    print()


if __name__ == "__main__":
    basic_chat()
    structured_output()
    tool_use()
