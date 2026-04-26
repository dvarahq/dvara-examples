///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2

/*
 * Streaming end-to-end demo — Java (HttpClient + line iterator).
 *
 * Three patterns:
 *   1. Basic streaming with content_filter handling.
 *   2. json_schema streaming — buffer-then-parse the accumulated content.
 *   3. Reconnect wrapper that retries on transport-class errors only.
 *
 * Usage:
 *   jbang StreamingDemo.java
 *   # set env first:
 *   export DVARA_URL="http://localhost:8080/v1"
 *   export DVARA_API_KEY="gw_<your-dvara-key>"
 *
 * See https://dvarahq.com/docs/cookbook/streaming-end-to-end for the
 * narrated walkthrough.
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

public class StreamingDemo {

    private static final String DVARA_URL =
            System.getenv().getOrDefault("DVARA_URL", "http://localhost:8080/v1");
    private static final String DVARA_API_KEY =
            System.getenv().getOrDefault("DVARA_API_KEY", "your-dvara-api-key");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        basicStreaming();
        jsonSchemaStreaming();
        reconnectDemo();
    }

    static void basicStreaming() throws Exception {
        System.out.println("=== 1. Basic streaming ===");
        String body = """
                {
                  "model": "gpt-4o",
                  "messages": [{"role": "user", "content": "Write a haiku about programming."}],
                  "stream": true
                }
                """;
        consume(body, content -> System.out.print(content));
        System.out.println("\n");
    }

    static void jsonSchemaStreaming() throws Exception {
        System.out.println("=== 2. json_schema streaming ===");
        String body = """
                {
                  "model": "gpt-4o",
                  "messages": [{"role": "user", "content": "Extract: John is 30 years old."}],
                  "response_format": {
                    "type": "json_schema",
                    "json_schema": {
                      "name": "person",
                      "schema": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "age": {"type": "integer"}
                        },
                        "required": ["name", "age"]
                      },
                      "strict": true
                    }
                  },
                  "stream": true
                }
                """;
        StringBuilder buffer = new StringBuilder();
        consume(body, buffer::append);
        JsonNode parsed = JSON.readTree(buffer.toString());
        System.out.printf("name: %s, age: %d%n%n",
                parsed.get("name").asText(), parsed.get("age").asInt());
    }

    /** Retry on transport-class failures only; never on content_filter. */
    static void reconnectDemo() throws Exception {
        System.out.println("=== 3. Reconnect wrapper ===");
        String body = """
                {
                  "model": "gpt-4o",
                  "messages": [{"role": "user", "content": "Say hello briefly."}],
                  "stream": true
                }
                """;
        int attempts = 3;
        long baseBackoffMs = 500;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                consume(body, content -> System.out.print(content));
                System.out.println("\n");
                return;
            } catch (HttpConnectTimeoutException | IOException e) {
                if (attempt == attempts - 1) throw e;
                Thread.sleep(baseBackoffMs * (1L << attempt));
            }
        }
    }

    /** Open the stream, parse each `data: …` line, hand the content delta off to the consumer. */
    static void consume(String body, java.util.function.Consumer<String> onContent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(DVARA_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + DVARA_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Stream<String>> response = HTTP.send(request, HttpResponse.BodyHandlers.ofLines());

        try (Stream<String> lines = response.body()) {
            lines.filter(line -> line.startsWith("data: "))
                 .map(line -> line.substring("data: ".length()))
                 .takeWhile(payload -> !"[DONE]".equals(payload))
                 .forEach(payload -> {
                     try {
                         JsonNode chunk = JSON.readTree(payload);
                         JsonNode choice = chunk.path("choices").path(0);
                         String delta = choice.path("delta").path("content").asText("");
                         if (!delta.isEmpty()) onContent.accept(delta);
                         if ("content_filter".equals(choice.path("finish_reason").asText(""))) {
                             onContent.accept("\n[stream stopped: governance enforcement]");
                         }
                     } catch (Exception ignored) { }
                 });
        }
    }
}
