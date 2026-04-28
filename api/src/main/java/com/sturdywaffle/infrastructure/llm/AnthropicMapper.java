package com.sturdywaffle.infrastructure.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;
import com.sturdywaffle.domain.port.Mapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AnthropicMapper implements Mapper {

    private static final String MODEL     = "claude-haiku-4-5";
    private static final String TOOL_NAME = "map_line";

    private final AnthropicClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Tool mapTool;
    private final String chartSystemPrompt;

    public AnthropicMapper(AnthropicClient client) throws IOException {
        this.client = client;
        this.mapTool = buildTool();
        this.chartSystemPrompt = buildChartPrompt();
    }

    @Override
    public Optional<MappingProposal> map(String supplierName, InvoiceLine line) {
        String userMsg = "Supplier: " + supplierName + "\nLine description: " + line.description()
                + "\nNet amount: " + line.net().value() + "\n\nMap this line to the correct BAS account.";

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(512)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(chartSystemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addTool(ToolUnion.ofTool(mapTool))
                .toolToolChoice(TOOL_NAME)
                .addUserMessage(userMsg)
                .build();

        Message response = client.messages().create(params);

        return response.content().stream()
                .filter(ContentBlock::isToolUse)
                .map(b -> b.toolUse().get())
                .filter(t -> TOOL_NAME.equals(t.name()))
                .findFirst()
                .map(toolUse -> {
                    try {
                        JsonNode root = mapper.valueToTree(toolUse._input());
                        return new MappingProposal(
                                root.path("accountCode").asText(),
                                root.path("reasoning").asText(),
                                root.path("confidence").asDouble());
                    } catch (Exception e) {
                        throw new ExtractionException("Failed to parse mapping result", e);
                    }
                });
    }

    private String buildChartPrompt() throws IOException {
        byte[] bytes = new ClassPathResource("seed/chart.json").getInputStream().readAllBytes();
        return "You are a Swedish accounting assistant. Map invoice line items to BAS chart of accounts.\n\n" +
                "Available accounts (JSON):\n" + new String(bytes) + "\n\n" +
                "Always call map_line with the most appropriate account code, your reasoning, and confidence (0-1).";
    }

    private Tool buildTool() {
        Tool.InputSchema.Properties props = Tool.InputSchema.Properties.builder()
                .putAdditionalProperty("accountCode", JsonValue.from(Map.of(
                        "type", "string", "description", "BAS account code, e.g. \"6540\"")))
                .putAdditionalProperty("reasoning", JsonValue.from(Map.of(
                        "type", "string", "description", "Why this account fits")))
                .putAdditionalProperty("confidence", JsonValue.from(Map.of(
                        "type", "number", "minimum", 0, "maximum", 1,
                        "description", "Confidence score between 0 and 1")))
                .build();

        Tool.InputSchema schema = Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(props)
                .required(List.of("accountCode", "reasoning", "confidence"))
                .build();

        return Tool.builder()
                .name(TOOL_NAME)
                .description("Map an invoice line to a BAS account code.")
                .inputSchema(schema)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
    }
}
