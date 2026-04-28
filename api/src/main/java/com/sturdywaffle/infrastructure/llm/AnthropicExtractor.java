package com.sturdywaffle.infrastructure.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.port.Extractor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicExtractor implements Extractor {

    private static final String MODEL = "claude-sonnet-4-6";
    private static final String TOOL_NAME = "extract_invoice";

    private final AnthropicClient client;
    private final Tool extractTool;

    public AnthropicExtractor(AnthropicClient client) {
        this.client = client;
        this.extractTool = buildTool();
    }

    @Override public String modelId() { return MODEL; }
    @Override public String promptVersion() { return "extract.v1"; }

    @Override
    public ExtractedInvoice extract(byte[] pdf) {
        String b64 = Base64.getEncoder().encodeToString(pdf);

        DocumentBlockParam doc = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(b64).build())
                .build();

        ContentBlockParam docBlock = ContentBlockParam.ofDocument(doc);
        ContentBlockParam textBlock = ContentBlockParam.ofText(
                TextBlockParam.builder().text("Extract all invoice fields using the extract_invoice tool.").build());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(1024)
                .system("You are an accounting assistant. Extract structured data from the invoice PDF provided. " +
                        "Always call extract_invoice — never respond with plain text.")
                .addTool(ToolUnion.ofTool(extractTool))
                .toolToolChoice(TOOL_NAME)
                .addUserMessageOfBlockParams(List.of(docBlock, textBlock))
                .build();

        Message response = client.messages().create(params);

        ToolUseBlock toolUse = response.content().stream()
                .filter(ContentBlock::isToolUse)
                .map(b -> b.toolUse().get())
                .filter(t -> TOOL_NAME.equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new ExtractionException("Model did not call " + TOOL_NAME));

        try {
            return parse(toolUse._input().convert(JsonNode.class));
        } catch (Exception e) {
            throw new ExtractionException("Failed to parse extraction result", e);
        }
    }

    private ExtractedInvoice parse(JsonNode root) {
        String supplierName  = root.path("supplierName").asText();
        String invoiceNumber = root.path("invoiceNumber").asText();
        LocalDate invoiceDate = LocalDate.parse(root.path("invoiceDate").asText());
        String currency      = root.path("currency").asText();
        Money netTotal       = Money.of(root.path("netTotal").asText());
        Money vatTotal       = Money.of(root.path("vatTotal").asText());
        Money grossTotal     = Money.of(root.path("grossTotal").asText());

        List<InvoiceLine> lines = new ArrayList<>();
        for (JsonNode lineNode : root.path("lines")) {
            lines.add(new InvoiceLine(
                    lineNode.path("description").asText(),
                    Money.of(lineNode.path("net").asText())));
        }

        return new ExtractedInvoice(supplierName, invoiceNumber, invoiceDate,
                currency, lines, netTotal, vatTotal, grossTotal);
    }

    private Tool buildTool() {
        JsonValue strProp    = JsonValue.from(Map.of("type", "string"));
        JsonValue numStrProp = JsonValue.from(Map.of("type", "string",
                "description", "Decimal number as string, e.g. \"1234.56\""));
        JsonValue lineItems  = JsonValue.from(Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "description", Map.of("type", "string"),
                                "net", Map.of("type", "string",
                                        "description", "Net amount as decimal string")),
                        "required", List.of("description", "net"))));

        Tool.InputSchema.Properties props = Tool.InputSchema.Properties.builder()
                .putAdditionalProperty("supplierName",  strProp)
                .putAdditionalProperty("invoiceNumber", strProp)
                .putAdditionalProperty("invoiceDate",   JsonValue.from(Map.of("type", "string",
                        "description", "ISO date YYYY-MM-DD")))
                .putAdditionalProperty("currency",      strProp)
                .putAdditionalProperty("netTotal",      numStrProp)
                .putAdditionalProperty("vatTotal",      numStrProp)
                .putAdditionalProperty("grossTotal",    numStrProp)
                .putAdditionalProperty("lines",         lineItems)
                .build();

        Tool.InputSchema schema = Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(props)
                .required(List.of("supplierName", "invoiceNumber", "invoiceDate",
                        "currency", "netTotal", "vatTotal", "grossTotal", "lines"))
                .build();

        return Tool.builder()
                .name(TOOL_NAME)
                .description("Extract structured fields from an invoice PDF.")
                .inputSchema(schema)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();
    }
}
