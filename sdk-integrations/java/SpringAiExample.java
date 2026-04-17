/**
 * Dvara Gateway — Spring AI Integration
 *
 * Add to your Spring Boot project:
 *
 *   <dependency>
 *       <groupId>org.springframework.ai</groupId>
 *       <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
 *   </dependency>
 *
 * application.yml:
 *   spring:
 *     ai:
 *       openai:
 *         base-url: http://localhost:8080/v1
 *         api-key: ${DVARA_API_KEY:your-dvara-api-key}
 *         chat:
 *           options:
 *             model: gpt-4o
 *             temperature: 0.7
 */

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpringAiExample {

    private final ChatClient chatClient;

    public SpringAiExample(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** Basic chat completion. */
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /** Structured output. */
    public CityInfo getCityInfo(String city) {
        return chatClient.prompt()
                .user("Tell me about " + city)
                .call()
                .entity(CityInfo.class);
    }

    /** Switch models at runtime. */
    public String chatWithModel(String message, String model) {
        return chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder().model(model).build())
                .call()
                .content();
    }

    record CityInfo(String name, String country, int population, List<String> landmarks) {}
}
