package com.sturdywaffle.infrastructure.config;

import com.openai.client.OpenAIClient;
import com.sturdywaffle.infrastructure.llm.OpenAiMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiMapperConfig {

    @Bean
    public OpenAiMapper primaryMapper(
            OpenAIClient client,
            @Value("${llm.openai.mapper.model}") String model,
            @Value("${llm.openai.mapper.max-tokens}") int maxTokens) throws IOException {
        return new OpenAiMapper(client, model, maxTokens, "map.v1");
    }

    @Bean
    public OpenAiMapper escalationMapper(
            OpenAIClient client,
            @Value("${llm.openai.escalation.model}") String model,
            @Value("${llm.openai.escalation.max-tokens}") int maxTokens) throws IOException {
        return new OpenAiMapper(client, model, maxTokens, "map.v1.escalation");
    }
}
