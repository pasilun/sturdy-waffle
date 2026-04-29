package com.sturdywaffle.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.ResponseFormatJsonSchema.JsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;
import com.sturdywaffle.domain.port.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OpenAiMapper implements Mapper {

    private static final Logger log = LoggerFactory.getLogger(OpenAiMapper.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final OpenAIClient client;
    private final String chartSystemPrompt;
    private final String model;
    private final int maxTokens;
    private final String promptVersion;
    private final ResponseFormatJsonSchema responseFormat;

    public OpenAiMapper(OpenAIClient client, String model, int maxTokens, String promptVersion) throws IOException {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.promptVersion = promptVersion;
        this.chartSystemPrompt = buildChartPrompt();
        this.responseFormat = buildResponseFormat();
    }

    @Override public String modelId() { return model; }
    @Override public String promptVersion() { return promptVersion; }

    @Override
    public Optional<MappingProposal> map(String supplierName, InvoiceLine line) {
        String userMsg = "Supplier: " + supplierName + "\nLine description: " + line.description()
                + "\nNet amount: " + line.net().value() + "\n\nMap this line to the correct BAS account.";

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(maxTokens)
                .addSystemMessage(chartSystemPrompt)
                .addUserMessage(userMsg)
                .responseFormat(responseFormat)
                .build();

        long start = System.currentTimeMillis();
        ChatCompletion response = client.chat().completions().create(params);
        long latencyMs = System.currentTimeMillis() - start;

        response.usage().ifPresent(u ->
                log.info("openai.map model={} latencyMs={} inputTokens={} outputTokens={} line='{}'",
                        model, latencyMs, u.promptTokens(), u.completionTokens(),
                        truncate(line.description(), 60)));

        return response.choices().get(0).message().content().map(json -> {
            try {
                JsonNode root = mapper.readTree(json);
                return new MappingProposal(
                        root.path("accountCode").asText(),
                        root.path("reasoning").asText(),
                        root.path("confidence").asDouble());
            } catch (Exception e) {
                throw new ExtractionException("Failed to parse mapping result", e);
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private String buildChartPrompt() throws IOException {
        String chart = new ClassPathResource("seed/chart.json").getContentAsString(StandardCharsets.UTF_8);
        return "You are a Swedish accounting assistant. Map invoice line items to BAS chart of accounts.\n\n" +
                "Available accounts (JSON):\n" + chart + "\n\n" +
                "Always respond with the most appropriate account code, your reasoning, and confidence (0-1).";
    }

    private ResponseFormatJsonSchema buildResponseFormat() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("accountCode", Map.of("type", "string", "description", "BAS account code, e.g. \"6540\""));
        props.put("reasoning",   Map.of("type", "string", "description", "Why this account fits"));
        props.put("confidence",  Map.of("type", "number", "description", "Confidence score between 0 and 1"));

        JsonSchema.Schema schema = JsonSchema.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(props))
                .putAdditionalProperty("required", JsonValue.from(List.of("accountCode", "reasoning", "confidence")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return ResponseFormatJsonSchema.builder()
                .jsonSchema(JsonSchema.builder()
                        .name("map_line")
                        .strict(true)
                        .schema(schema)
                        .build())
                .build();
    }
}
