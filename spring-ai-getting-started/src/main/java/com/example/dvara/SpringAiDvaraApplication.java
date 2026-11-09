package com.example.dvara;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Sends one prompt through Spring AI's ChatClient and prints the reply.
 *
 * The ChatClient is the standard Spring AI one. It reaches the DVARA LLM
 * Gateway only because application.yml sets spring.ai.openai.base-url and
 * spring.ai.openai.api-key. Change the prompt with --app.prompt="...".
 */
@SpringBootApplication
public class SpringAiDvaraApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDvaraApplication.class, args);
    }

    @Bean
    CommandLineRunner ask(ChatClient.Builder builder, @Value("${app.prompt}") String prompt) {
        return args -> {
            ChatClient chatClient = builder.build();

            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            System.out.println();
            System.out.println("Prompt:   " + prompt);
            System.out.println("Model:    " + response.getMetadata().getModel());
            System.out.println("Reply:    " + response.getResult().getOutput().getText());
            System.out.println("Response: " + response.getMetadata().getId());
            System.out.println();
        };
    }
}
