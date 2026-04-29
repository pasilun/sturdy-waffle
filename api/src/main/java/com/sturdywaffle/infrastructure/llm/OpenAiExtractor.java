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
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.port.Extractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiExtractor implements Extractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final OpenAIClient client;
    private final String model;
    private final int maxTokens;
    private final ResponseFormatJsonSchema responseFormat;

    public OpenAiExtractor(
            OpenAIClient client,
            @Value("${llm.openai.extractor.model}") String model,
            @Value("${llm.openai.extractor.max-tokens}") int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.responseFormat = buildResponseFormat();
    }

    @Override public String modelId() { return model; }
    @Override public String promptVersion() { return "extract.v1"; }

    @Override
    public ExtractedInvoice extract(byte[] pdf) {
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new ExtractionException("Failed to read PDF", e);
        }

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(maxTokens)
                .addSystemMessage("You are an accounting assistant. Extract structured data from the invoice text provided. " +
                        "Always fill all required fields exactly as they appear in the invoice.")
                .addUserMessage(text + "\n\nExtract all invoice fields.")
                .responseFormat(responseFormat)
                .build();

        long start = System.currentTimeMillis();
        ChatCompletion response = client.chat().completions().create(params);
        long latencyMs = System.currentTimeMillis() - start;

        response.usage().ifPresent(u ->
                log.info("openai.extract model={} latencyMs={} inputTokens={} outputTokens={}",
                        model, latencyMs, u.promptTokens(), u.completionTokens()));

        String json = response.choices().get(0).message().content()
                .orElseThrow(() -> new ExtractionException("Model returned no content"));

        try {
            return parse(mapper.readTree(json));
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

    private ResponseFormatJsonSchema buildResponseFormat() {
        Map<String, Object> lineItemProps = new LinkedHashMap<>();
        lineItemProps.put("description", Map.of("type", "string"));
        lineItemProps.put("net", Map.of("type", "string", "description", "Net amount as decimal string"));

        Map<String, Object> lineItemSchema = new LinkedHashMap<>();
        lineItemSchema.put("type", "object");
        lineItemSchema.put("properties", lineItemProps);
        lineItemSchema.put("required", List.of("description", "net"));
        lineItemSchema.put("additionalProperties", false);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("supplierName",  Map.of("type", "string"));
        props.put("invoiceNumber", Map.of("type", "string"));
        props.put("invoiceDate",   Map.of("type", "string", "description", "ISO date YYYY-MM-DD"));
        props.put("currency",      Map.of("type", "string"));
        props.put("netTotal",      Map.of("type", "string", "description", "Decimal number as string, e.g. \"1234.56\""));
        props.put("vatTotal",      Map.of("type", "string", "description", "Decimal number as string"));
        props.put("grossTotal",    Map.of("type", "string", "description", "Decimal number as string"));
        props.put("lines",         Map.of("type", "array", "items", lineItemSchema));

        JsonSchema.Schema schema = JsonSchema.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(props))
                .putAdditionalProperty("required", JsonValue.from(List.of(
                        "supplierName", "invoiceNumber", "invoiceDate", "currency",
                        "netTotal", "vatTotal", "grossTotal", "lines")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return ResponseFormatJsonSchema.builder()
                .jsonSchema(JsonSchema.builder()
                        .name("extract_invoice")
                        .strict(true)
                        .schema(schema)
                        .build())
                .build();
    }
}
