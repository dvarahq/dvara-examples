"""
Dvara Gateway — LangChain Integration

Usage:
    pip install langchain-openai
    export DVARA_URL="http://localhost:8080/v1"
    export DVARA_API_KEY="your-dvara-api-key"
    python langchain_example.py
"""

import os

from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

DVARA_URL = os.getenv("DVARA_URL", "http://localhost:8080/v1")
DVARA_API_KEY = os.getenv("DVARA_API_KEY", "your-dvara-api-key")

llm = ChatOpenAI(
    model="gpt-4o",
    openai_api_base=DVARA_URL,
    openai_api_key=DVARA_API_KEY,
    temperature=0.7,
)


def basic_chat():
    """Basic invocation."""
    print("=== Basic Chat ===")
    response = llm.invoke("What is the capital of France?")
    print(response.content)
    print()


def chain_example():
    """Prompt template chain."""
    print("=== Chain ===")
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a {role}. Answer concisely."),
        ("human", "{question}"),
    ])
    chain = prompt | llm
    response = chain.invoke({"role": "historian", "question": "Who built the pyramids?"})
    print(response.content)
    print()


def streaming_example():
    """Streaming output."""
    print("=== Streaming ===")
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a poet."),
        ("human", "{topic}"),
    ])
    chain = prompt | llm
    for chunk in chain.stream({"topic": "Write a short poem about the moon"}):
        print(chunk.content, end="")
    print("\n")


if __name__ == "__main__":
    basic_chat()
    chain_example()
    streaming_example()
