---
title: "Invoice-to-Journal: add OpenAI as a second LLM provider"
type: session
created: 2026-04-29
updated: 2026-04-29
tags: [openai, anthropic, multi-provider, llm, java]
---

# Add OpenAI as a second LLM provider

**Status: complete. Shipped 2026-04-29. Eval: 6/6 fixtures pass.**

**Trigger:** Anthropic billing is down; credits cannot be topped up. Need OpenAI as an active fallback. When Anthropic billing recovers, switching back should be a one-line config change.

**Prerequisite:** An OpenAI API key in `api/.env` as `OPENAI_API_KEY`.

## Design principle

This is NOT a provider swap — it is a provider addition. Both Anthropic and OpenAI implementations live in the codebase permanently. The active provider is controlled by a single config key (`llm.provider`) that can be flipped via env var without recompile. The interfaces (`Extractor`, `Mapper`) already exist; this work just adds a second implementation behind each. See [[extractor-as-provider-seam]] and [[llm-provider-portability]].

## Switching mechanism

```bash
# Use Anthropic (default, works when billing is up)
LLM_PROVIDER=anthropic ./dev.sh

# Use OpenAI (fallback when Anthropic billing is down)
LLM_PROVIDER=openai ./dev.sh
```

Or flip `llm.provider` in `application.yml`. No recompile. No code change.

## Model mapping

| Anthropic              | OpenAI         | Role                         |
|------------------------|----------------|------------------------------|
| `claude-sonnet-4-6`    | `gpt-4o`       | extraction + escalation      |
| `claude-haiku-4-5`     | `gpt-4o-mini`  | primary mapping              |

## The one real trade-off: PDF handling

Anthropic accepts raw PDFs as native `document` content blocks — vision-aware, layout-preserving. OpenAI chat completions does not. The OpenAI extractor will use Apache PDFBox to extract the text layer and pass it as a text message.

Acceptable because all fixtures are digitally generated PDFs with clean text layers. Scanned PDFs would need a rasterize-to-images path — out of scope here.

Prompt caching: Anthropic uses explicit `cache_control` markers (and logs `cacheReadInputTokens`). OpenAI caches automatically above ~1024 tokens with no API surface. The OpenAI implementations simply omit that log field.

---

## Wiring mechanism: `@ConditionalOnProperty`

Each provider's config class and extractor carries:

```java
// Anthropic beans — active by default, explicit when llm.provider=anthropic
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)

// OpenAI beans — only active when llm.provider=openai
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
```

`matchIfMissing = true` on the Anthropic side means the app keeps working without any `llm.provider` config (safe default).

---

## Step-by-step plan

### Step 1 — Gradle: add deps, keep Anthropic
**File:** `api/build.gradle.kts`

Keep `com.anthropic:anthropic-java:2.27.0`. Add:
```
implementation("com.openai:openai-java:2.x.x")   // check Maven Central for latest 1.x
implementation("org.apache.pdfbox:pdfbox:3.0.x")  // PDF text extraction
```

### Step 2 — `application.yml`: add OpenAI config block, flip provider
**File:** `api/src/main/resources/application.yml`

```yaml
llm:
  provider: openai          # ← flip here to switch; anthropic is the default
  anthropic:
    extractor:
      model: claude-sonnet-4-6
      max-tokens: 1024
    mapper:
      model: claude-haiku-4-5
      max-tokens: 512
    escalation:
      model: claude-sonnet-4-6
      max-tokens: 1024
  openai:
    extractor:
      model: gpt-4o
      max-tokens: 1024
    mapper:
      model: gpt-4o-mini
      max-tokens: 512
    escalation:
      model: gpt-4o
      max-tokens: 1024
```

Both sections live in the file permanently. No keys are removed.

### Step 3 — `api/.env`: add OpenAI key, keep Anthropic key
**File:** `api/.env`

Add `OPENAI_API_KEY=sk-...` alongside the existing `ANTHROPIC_API_KEY`. Spring only tries to resolve the key for the active provider's config class (due to `@ConditionalOnProperty`), so having both keys in `.env` is harmless and enables instant switching.

### Step 4 — `AnthropicConfig.java`: add `@ConditionalOnProperty`
**File:** `...config/AnthropicConfig.java`

Add to the class: `@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)`. No other changes.

### Step 5 — `AnthropicExtractor.java`: add `@ConditionalOnProperty`
**File:** `...llm/AnthropicExtractor.java`

Add to the class: `@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)`. No other changes.

### Step 6 — `MapperConfig.java` → `AnthropicMapperConfig.java`
**File:** `...config/MapperConfig.java`

Rename to `AnthropicMapperConfig`. Add `@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)` to the class. No logic changes.

### Step 7 — New `OpenAiConfig.java`
**File:** `...config/OpenAiConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiConfig {
    @Bean
    public OpenAIClient openAiClient(@Value("${OPENAI_API_KEY}") String apiKey) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
```

### Step 8 — New `OpenAiExtractor.java`
**File:** `...llm/OpenAiExtractor.java`

Implements `Extractor`. `@Component` + `@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")`.

Key implementation notes:
1. Constructor injects `OpenAIClient`, `${llm.openai.extractor.model}`, `${llm.openai.extractor.max-tokens}`
2. PDF handling: `PDDocument.load(pdf)` + `PDFTextStripper.getText()` → plain text string
3. Sends as a `ChatCompletionCreateParams` with:
   - system message (same text as Anthropic's)
   - user message: the extracted text + "Extract all invoice fields using the extract_invoice function."
   - one function definition with the same JSON Schema (supplierName, invoiceNumber, invoiceDate, currency, netTotal, vatTotal, grossTotal, lines)
   - `toolChoice` forced to `extract_invoice`
4. Response: find the `tool_calls` entry with name `extract_invoice`, parse `.function().arguments()` as JSON via Jackson
5. `parse(JsonNode)` method: identical to `AnthropicExtractor.parse()` — same field paths, same types
6. Log line: `openai.extract model={} latencyMs={} inputTokens={} outputTokens={}` (no cacheReadTokens)
7. `modelId()` returns `model`, `promptVersion()` returns `"extract.v1"`

### Step 9 — New `OpenAiMapper.java`
**File:** `...llm/OpenAiMapper.java`

Implements `Mapper`. NOT annotated `@Component` — constructed by `OpenAiMapperConfig` (same pattern as `AnthropicMapper`).

Key implementation notes:
1. Constructor: `(OpenAIClient client, String model, int maxTokens, String promptVersion)`
2. `buildChartPrompt()`: identical to `AnthropicMapper.buildChartPrompt()` — reads `seed/chart.json`, same wrapping text
3. `buildTool()`: same JSON Schema (accountCode, reasoning, confidence)
4. `map(supplierName, line)`: builds `ChatCompletionCreateParams` with system message (chart prompt), user message (same format as Anthropic), function forced to `map_line`
5. Parse result: same `MappingProposal` construction
6. Log line: `openai.map model={} latencyMs={} inputTokens={} outputTokens={} line='{}'` (no cacheReadTokens)

### Step 10 — New `OpenAiMapperConfig.java`
**File:** `...config/OpenAiMapperConfig.java`

```java
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
```

### Step 11 — `EvalContextSmokeTest.java`: stub both keys
**File:** `...eval/EvalContextSmokeTest.java`

Change `@TestPropertySource` to stub both keys so the test passes regardless of which provider is active:

```java
@TestPropertySource(properties = {
    "ANTHROPIC_API_KEY=test-stub",
    "OPENAI_API_KEY=test-stub"
})
```

Update the comment to drop the Anthropic-specific reference.

---

## What does NOT need to change

- `PipelineServiceEscalateTest.java` — model ID strings are just mock return values, not tied to real impl
- `JdbcPersisterIntegrationTest.java` — model strings are arbitrary test data, not tied to real impl
- `PipelineService.java` — zero provider knowledge; holds `Extractor` and `Mapper` interfaces
- All domain, persistence, frontend, and API layer code

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| PDFBox can't extract text (scanned PDF) | Low — all fixtures are digital | High — extractor returns garbage | Run `./gradlew eval` after step 11; catch it early |
| GPT-4o-mini quality differs from Haiku on devops.pdf | Medium | Medium | Eval harness catches it; escalation path exists; model is config-driven |
| OpenAI Java SDK API shape differs from assumed | Medium — SDK is recent | Low — compile errors catch it immediately | Fix at compile time |
| Both provider beans wired simultaneously (misconfigured conditionals) | Low | High — Spring fails to start | Each side uses `matchIfMissing` correctly; only one provider's beans load |

---

## Files touched (summary)

| Action | File |
|---|---|
| Edit | `api/build.gradle.kts` |
| Edit | `api/src/main/resources/application.yml` |
| Edit | `api/.env` |
| Edit | `AnthropicConfig.java` (add `@ConditionalOnProperty`) |
| Edit | `AnthropicExtractor.java` (add `@ConditionalOnProperty`) |
| Rename + Edit | `MapperConfig.java` → `AnthropicMapperConfig.java` (add conditional) |
| Create | `OpenAiConfig.java` |
| Create | `OpenAiExtractor.java` |
| Create | `OpenAiMapper.java` |
| Create | `OpenAiMapperConfig.java` |
| Edit | `EvalContextSmokeTest.java` |

Total: 11 files. Pipeline, domain, persistence, frontend, and API untouched.
