///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j-open-ai:1.0.0-beta1

/**
 * Dvara Gateway — LangChain4j Integration
 *
 * Usage (with JBang):
 *   export DVARA_URL="http://localhost:8080/v1"
 *   export DVARA_API_KEY="your-dvara-api-key"
 *   jbang LangChain4jExample.java
 *
 * Or add the Maven dependency to your project:
 *   <dependency>
 *       <groupId>dev.langchain4j</groupId>
 *       <artifactId>langchain4j-open-ai</artifactId>
 *       <version>1.0.0-beta1</version>
 *   </dependency>
 */

import dev.langchain4j.model.openai.OpenAiChatModel;

public class LangChain4jExample {

    static final String DVARA_URL = System.getenv().getOrDefault("DVARA_URL", "http://localhost:8080/v1");
    static final String DVARA_API_KEY = System.getenv().getOrDefault("DVARA_API_KEY", "your-dvara-api-key");

    public static void main(String[] args) {
        basicChat();
        multiModel();
    }

    static void basicChat() {
        System.out.println("=== Basic Chat (GPT-4o) ===");
        var model = OpenAiChatModel.builder()
                .baseUrl(DVARA_URL)
                .apiKey(DVARA_API_KEY)
                .modelName("gpt-4o")
                .build();

        String response = model.chat("What is the capital of Germany?");
        System.out.println(response);
        System.out.println();
    }

    static void multiModel() {
        System.out.println("=== Multi-Model ===");
        String[] models = {"gpt-4o", "claude-sonnet-4-20250514", "gemini-2.0-flash"};

        for (String modelName : models) {
            try {
                var model = OpenAiChatModel.builder()
                        .baseUrl(DVARA_URL)
                        .apiKey(DVARA_API_KEY)
                        .modelName(modelName)
                        .maxTokens(50)
                        .build();

                String response = model.chat("Say hello in one sentence.");
                System.out.printf("%s: %s%n", modelName, response);
            } catch (Exception e) {
                System.out.printf("%s: Error — %s%n", modelName, e.getMessage());
            }
        }
        System.out.println();
    }
}
