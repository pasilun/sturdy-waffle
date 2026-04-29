package com.sturdywaffle.infrastructure.config;

import com.anthropic.client.AnthropicClient;
import com.sturdywaffle.infrastructure.llm.AnthropicMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicMapperConfig {

    @Bean
    public AnthropicMapper primaryMapper(
            AnthropicClient client,
            @Value("${llm.anthropic.mapper.model}") String model,
            @Value("${llm.anthropic.mapper.max-tokens}") int maxTokens) throws IOException {
        return new AnthropicMapper(client, model, maxTokens, "map.v1");
    }

    @Bean
    public AnthropicMapper escalationMapper(
            AnthropicClient client,
            @Value("${llm.anthropic.escalation.model}") String model,
            @Value("${llm.anthropic.escalation.max-tokens}") int maxTokens) throws IOException {
        return new AnthropicMapper(client, model, maxTokens, "map.v1.escalation");
    }
}
