"""Structured outputs — get JSON responses matching a schema."""
import os

from openai import OpenAI
import json

client = OpenAI(
    api_key=os.environ.get("DVARA_API_KEY", "any-key"),
    base_url="http://localhost:8080/v1"
)

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "List 3 programming languages"}],
    response_format={
        "type": "json_schema",
        "json_schema": {
            "name": "languages",
            "schema": {
                "type": "object",
                "properties": {
                    "languages": {
                        "type": "array",
                        "items": {"type": "string"}
                    }
                },
                "required": ["languages"]
            }
        }
    }
)

result = json.loads(response.choices[0].message.content)
print(result)
