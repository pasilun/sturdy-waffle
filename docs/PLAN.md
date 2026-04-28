# Invoice-to-Journal Entry — Implementation Plan

> Companion to `SPEC.md`. The spec describes **what**; this describes **how**. Every decision below is traceable to a SPEC section.

---

## 1. Architecture at a glance

```
┌──────────────┐   PDF   ┌──────────────────────────────────────────┐
│  React UI    │────────▶│              HTTP API                    │
│  (Vite + TS) │◀────────│  POST /invoices  GET /invoices/:id        │
└──────────────┘  JSON   │  GET /invoices/:id/pdf                   │
                         │  POST /invoices/:id/decision             │
                         └─────────────────┬────────────────────────┘
                                           │
                                           ▼
                         ┌──────────────────────────────────────────┐
                         │             Pipeline                     │
                         │  store → extract → validate → map →      │
                         │  assemble → persist                      │
                         └─────────────────┬────────────────────────┘
                                           │
            ┌──────────────┬───────────────┼───────────────┬─────────────┐
            ▼              ▼               ▼               ▼             ▼
        Filesystem    Extractor       Decimal         Mapper       Postgres
        (PDF blob)    (Anthropic;     validator       chain        (NUMERIC,
                       OpenAi swap)   (net+vat=tot)   (rule→LLM)    Flyway)
```

Three principles, in priority order:
1. **The LLM never does arithmetic.** Mapping is judgment; balancing is code. (§9.2)
2. **Every layer is replaceable.** Mapper, extractor, store, UI — all behind interfaces, so live extensions land as new files, not edits to old ones. (§9.1)
3. **Trust is surfaced, not asserted.** Reasoning + confidence ride alongside every mapping decision into the UI. (§9.3)

### Package structure

```
com.sturdywaffle/
├── domain/                     ← zero Spring / SDK imports
│   ├── model/                  ← Money, Account, InvoiceLine, ExtractedInvoice,
│   │                               MappingProposal, Posting, SuggestionId
│   ├── port/                   ← Extractor, Mapper (interfaces the domain owns)
│   ├── service/                ← Validator, Assembler (pure logic, no framework)
│   └── exception/              ← ExtractionException, ValidationException
├── application/                ← PipelineService (orchestration; depends on domain)
├── infrastructure/             ← Spring, JDBC, Anthropic SDK
│   ├── config/                 ← PostgresConfig
│   ├── llm/                    ← AnthropicExtractor, AnthropicMapper (Phase 2)
│   ├── persistence/            ← repositories (Phase 2)
│   └── seed/                   ← ChartSeeder
├── web/                        ← InvoiceController, HealthController
└── eval/                       ← EvalRunner (Phase 2)
```

Dependency rule: `domain` → nothing; `application` → `domain`; `infrastructure` → `domain` + Spring + SDK; `web` → `application` + `domain.model`.

## 2. Stack

| Layer | Choice | Why |
|---|---|---|
| Frontend | React + TS + Vite | Mandatory per §4.6. Vite for fast dev loop. |
| Styling | Tailwind | Zero cycles on CSS naming. |
| Backend | Java 21 + Spring Boot 3 | User preference. Java 21 LTS; Spring Boot is the path of least resistance for a single-user CRUD-plus-LLM app. |
| API build | Gradle (Kotlin DSL) | Daemon keeps incremental builds and reloads snappy. |
| HTTP | Spring Web MVC | Synchronous fits the inline pipeline; WebFlux would be overkill for one user. |
| DB | Postgres 16 via `org.postgresql:postgresql` + `io.zonky.test:embedded-postgres` + Spring Data JDBC + Flyway | Native `NUMERIC(18,2)` handling; real Postgres dialect; zero-ops. Embedded-postgres downloads a real Postgres binary on first boot (~30 MB, then cached); no Docker, no daemon, no install. JDBC over JPA — no lazy-loading or session-cache surprises. |
| Migrations | Flyway (`V1__init.sql`, etc.) | Versioned, idempotent on boot. |
| Decimals | `java.math.BigDecimal`, canonical scale 2 | The Java money type. **Gotcha:** `BigDecimal.equals` is scale-sensitive (`1.20 ≠ 1.2`). Canonicalize on parse, then `.equals` is the strict check the SPEC asks for. (§7) |
| LLM SDK | `com.anthropic:anthropic-java` | Native PDF document blocks; tool-use for structured output; `cache_control` for prompt caching. |
| Model | `claude-sonnet-4-6` for both calls | Cost/latency fits the 15s budget (§10). Swap to Opus 4.7 for the live harder PDF. |
| Tests | JUnit 5 + AssertJ | Unit tests on validator + assembler + mapper chain. |
| Repo | `web/` (pnpm + Vite), `api/` (Gradle Spring Boot), root `dev.sh` runs both | Two languages → two build systems. Don't unify. DTO types are hand-mirrored in `web/src/types/` — the API surface is small enough that codegen is not worth the build step. |

## 3. Data model

```
accounts          (code PK, name, type, normal_side)        -- seeded from chart
invoices          (id, supplier_name, invoice_number, invoice_date,
                   currency, net, vat, gross, pdf_path, created_at)
extractions       (id, invoice_id FK, raw_json, model, prompt_version,
                   latency_ms, created_at)
suggestions       (id, invoice_id FK, extraction_id FK, model,
                   prompt_version, latency_ms, created_at)
postings          (id, suggestion_id FK, line_index, account_code FK,
                   debit, credit, description, reasoning, confidence)
decisions         (id, suggestion_id FK UNIQUE, status, decided_at, note)
audit_events      (id, entity, entity_id, event, payload_json, created_at)
```

Notes:
- `net`, `vat`, `gross`, `debit`, `credit` are **`NUMERIC(18,2)`**. Postgres preserves scale exactly, so the JDBC driver round-trips `BigDecimal` losslessly. No string-encoded money, no custom storage converter — canonicalize at the parse boundary (§7) and the column type takes care of the rest.
- `account_code` is a foreign key — postings reference the chart, never embed it. This is the wedge that makes edit-before-approve a pure frontend change later. (§4.5)
- `audit_events` is unused by v1 code paths but the table exists. Adding the audit-log feature in §13 is a write, not a migration.

## 4. Pipeline

A single Spring `@Service` method `PipelineService.run(byte[] pdf): SuggestionId` chains:

1. **Store.** Write PDF to `data/uploads/<uuid>.pdf`. Insert `invoices` stub.
2. **Extract.** Call the configured `Extractor` (see §6). Output: `supplier`, `invoice_number`, `invoice_date`, `currency`, `lines[] { description, net }`, `net_total`, `vat_total`, `gross_total`. No reasoning, no confidence — extraction is mechanical. The pipeline holds an `Extractor` reference, not an SDK.
3. **Validate.** Parse all amounts to `BigDecimal` at scale 2. Assert `sum(lines.net).equals(net_total)` and `net_total.add(vat_total).equals(gross_total)`. Failure → typed `ExtractionException` → `@ControllerAdvice` returns 422 with the specific mismatch. No partial entry persisted. (§4.3)
4. **Map.** Run mapper chain over each line (see §6). Each line emits `{ account_code, reasoning, confidence }`. The VAT-in and supplier-liability accounts are picked deterministically from the chart, not by the LLM.
5. **Assemble.** Build postings:
   - Debit cost account per line (LLM-chosen).
   - Debit VAT-in account for `vat_total`.
   - Credit supplier-liability account for `gross_total`.
   - Assert each posting has exactly one of `debit` or `credit` non-zero — the SPEC §4.3 "debit OR credit" invariant made explicit in code. Also enforced as a `CHECK ((debit IS NOT NULL AND credit IS NULL) OR (debit IS NULL AND credit IS NOT NULL))` constraint in the Flyway migration, so no write path can violate it.
   - Assert total debits `.equals` total credits as `BigDecimal`. Tautological after step 3, but the check stays — defense in depth.
6. **Persist.** One `@Transactional` boundary: write `extractions`, `suggestions`, `postings`. Emit audit event.

Steps 2 and 4 are the only LLM calls. Steps 3, 5, and 6 are pure code.

## 5. LLM design

Two calls, two prompts, two output schemas. Both prompts live as classpath resources under `api/src/main/resources/prompts/` as versioned files (`extract.v1.txt`, `map.v1.txt`). The version string is persisted on every row so we can correlate quality regressions to prompt edits.

**Extraction.** PDF in (vision/document block), strict JSON out via the provider's structured-output mechanism (Anthropic tool-use; OpenAI `response_format: json_schema`). Refusal to invent missing fields — they come back as `null` and validation handles the rest.

**Mapping.** Input: extracted line items + the full chart of accounts (20 rows, fits trivially) + supplier name. Output per line: `{ account_code, reasoning (≤140 chars), confidence (0–1) }`. The prompt explicitly forbids returning an account not in the provided chart, and the structured-output schema enforces it via enum.

Prompt caching: the chart of accounts and system prompt are stable. On Anthropic, mark them with `cache_control: ephemeral`; on OpenAI, caching above ~1024 tokens is automatic. Worth ~hundreds of ms on warm runs.

## 6. Provider seams

Two interfaces in `domain/port/`, both provider-agnostic. The pipeline (§4) only knows about these — it never imports an SDK.

```java
// domain/port/Extractor.java
public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
}

// domain/port/Mapper.java
public interface Mapper {
    Optional<MappingProposal> map(String supplierName, InvoiceLine line);
}
```

v1 wiring (Phase 2):
- `Extractor`: single bean, `infrastructure/llm/AnthropicExtractor`. PDF in via `document` block; structured output via tool-use.
- `Mapper`: `List<Mapper>` Spring bean = `[AnthropicMapper]`. The first mapper to return a present `Optional` wins.

These are the two pivot points for live extensions:
- **Provider swap** (e.g. OpenAI). Add `OpenAiExtractor` and `OpenAiLlmMapper` next to the Anthropic ones, switch beans via a `@ConditionalOnProperty("llm.provider")` config. The pipeline does not move. The asymmetry to budget for: OpenAI does not accept PDFs as a content block — the `OpenAiExtractor` either rasterizes pages (PDFBox dep) or uploads via the Files API. That cost lives entirely inside the new `Extractor` impl.
- **Supplier-preference register** (SPEC §13). Prepend `SupplierRuleMapper` to the mapper chain. One new `@Component`, one line in chain config — no changes to `LlmMapper`, the pipeline, or the schema.

## 7. Numeric handling

- Every amount enters as a `String` (from JSON / Postgres) and is parsed once via `new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY)`. `UNNECESSARY` throws if the input has more than two fractional digits — that is an extraction bug, not something to round away.
- All money is wrapped in a tiny `Money` value type (`record Money(BigDecimal value)`) with `add`, `equals`, and `toString`. This keeps `BigDecimal` out of business code and the scale gotcha out of reach.
- `Money.equals` delegates to `BigDecimal.equals`, which is safe **because every Money instance is canonicalized to scale 2 at the boundary**. Anywhere else, `compareTo == 0` would be required and that is exactly what we want to avoid scattering.
- Storage round-trip: a `Converter<Money, BigDecimal>` pair registered with Spring Data JDBC. The DB side is `NUMERIC(18,2)` (§3); Spring just sees `BigDecimal` and we keep `Money` in domain code.
- Currency is captured but v1 assumes a single currency per invoice (§11 anti-goal: multi-currency).

## 8. Persistence

No Docker. Postgres 16 is started in-process by `io.zonky.test:embedded-postgres` via a small Spring `@Configuration` class:

```java
@Bean
EmbeddedPostgres embeddedPostgres() throws IOException {
    return EmbeddedPostgres.builder()
        .setPort(5432)
        .setDataDirectory(Path.of("data/pg"))
        .start();
}
```

`data/pg/` is created on first boot. On subsequent boots Postgres reattaches to the existing data directory — **state is preserved across restarts** (SPEC §4.4). `data/pg/` is gitignored; `data/` is the complete durable artifact (`pg` + `uploads/`).

JDBC URL `jdbc:postgresql://localhost:5432/invoices`, no credentials needed (embedded runs as the OS user). Flyway migrations under `api/src/main/resources/db/migration/` apply on boot. Chart of accounts seeded by an `ApplicationRunner` reading `api/src/main/resources/seed/chart.json` — idempotent (`INSERT ... ON CONFLICT (code) DO NOTHING`). PDFs on disk under `data/uploads/`.

**One first-boot caveat:** `io.zonky.test:embedded-postgres` downloads a Postgres binary (~30 MB) on the very first run and caches it under `~/.zonky/` (or similar). This requires an internet connection once — worth doing before the interview. `./gradlew :api:bootRun` the evening before will pre-warm it.

## 9. Frontend

Two views, one route each:

- `/` — upload zone. Drag-drop a PDF. On success, navigate to review.
- `/invoices/:id` — split view. Left: PDF in `<iframe src="/invoices/:id/pdf">` — the API reads from `data/uploads/` and streams with `Content-Type: application/pdf`; browsers render natively, no `react-pdf` dep. Right: postings table with one row per posting. For LLM-decided rows, the reasoning sits inline as a muted line under the account name, and confidence renders as a small bar (green ≥ 0.8, amber 0.5–0.8, red < 0.5). Approve / Decline buttons at the bottom.

State via TanStack Query. No global store. No router beyond the two routes.

## 10. Eval harness

A Gradle task: `./gradlew :api:eval`. Backed by `EvalRunner` under the `eval` Spring profile, it calls a `PipelineService.evaluate(byte[])` method that runs the full pipeline (extract → validate → map → assemble) and returns an `EvalResult` — without persisting to DB. The HTTP `run()` path is unchanged.

Output format:
```
case             extract    map@1    confidence    latency
sample.pdf       PASS       3/3      0.91 avg      4.2s
rent.pdf         PASS       1/1      0.88          2.1s
lunch.pdf        PASS       1/1      0.74          2.4s
```

- **extract PASS** — no `ExtractionException` or `ValidationException`; amounts balance.
- **map N/N** — N lines whose first posting matched the expected account code in `{name}.expected.json`.
- **confidence** — average `MappingProposal.confidence` across all lines.
- **latency** — wall-clock ms for the full evaluate call.

**Expected JSON format** (`{name}.expected.json`, one per fixture):
```json
{
  "netTotal": "8000.00",
  "vatTotal": "2000.00",
  "grossTotal": "10000.00",
  "lines": [
    { "accountCode": "5010" }
  ]
}
```

Fixtures:
1. `sample.pdf` — the provided interview sample (multiple lines).
2. `rent.pdf` — Stockholms Fastigheter AB, lokalhyra, SEK 8000+2000 VAT → account `5010`.
3. `lunch.pdf` — Restaurang Söder AB, teamlunch, SEK 480+57.60 VAT → account `7731` (boundary case; lower confidence expected).

The harness is the canary for prompt edits and model swaps; run it after any change under `resources/prompts/`.

## 11. Live extension defenses (answers SPEC §13)

| Extension | Defense already in place | Cost to land |
|---|---|---|
| Multi-page / missing-field PDF | Extraction is one prompt + structured output; Anthropic document blocks are page-agnostic. Validation produces typed errors per missing field. | Add `extract.v2.txt`; bump version constant. |
| Provider swap (OpenAI, etc.) | `Extractor` + `Mapper` interfaces (§6); pipeline holds no SDK references. | Two new `@Component`s + a `llm.provider` switch. PDF input on OpenAI requires rasterize-or-upload — that work is contained in the new `Extractor`. |
| Supplier-preference register | Mapper chain + `Mapper` interface (§6). | New `@Component` + one line in chain config. |
| Audit log | `audit_events` table exists; pipeline already emits events. | Frontend page that reads the table. |
| One-line-to-many splits | `postings.line_index` allows N postings per line. Assembler does not assume 1:1. | Mapping schema gains `splits[]` per line; assembler unchanged. |
| Edit-before-approve | `account_code` is FK, not embedded. | Frontend-only: editable cell + PATCH endpoint. |

If a live extension does not appear in this table, the design has a gap — flag it rather than wing it.

## 12. Build order

1. ✅ Repo skeleton: `web/` (Vite) + `api/` (Spring Boot init via `start.spring.io`) + `dev.sh` (starts both apps; no DB step needed) + embedded-postgres config + Flyway migrations + chart seed + `PipelineService` stub. Domain layer package structure introduced (§1 package structure).
2. ✅ Extraction call + `Money` type + validator + mapper + assembler + `PipelineService` wired.
3. ✅ Eval harness: `PipelineService.evaluate()`, `EvalResult`, expected JSON files, `EvalRunner` table output. All three fixtures green (`3 passed, 0 failed`). Actual results: lunch 1/1 @ 0.95, rent 1/1 @ 0.99, sample 3/3 @ 0.95. `PostgresConfig` and `ChartSeeder` gated `@Profile("!eval")`; `application-eval.yml` excludes DataSource/Flyway autoconfiguration.
4. Persistence + decision endpoint. cURL round-trip works.
5. Frontend: upload page → review page → approve/decline.
6. README: run instructions, what was built, what was deferred, how to extend.
7. Smoke test: cold clone → `./dev.sh` (first run downloads embedded Postgres binary; ~30s) → upload sample → approve → restart → suggestion still there.

Each step is a green checkpoint. If step N is red, do not start step N+1.

## 13. Deliberately not built

Beyond the §11 anti-goals already in SPEC:

- No streaming responses. The 15s budget (§10) is comfortable for non-streamed tool-use.
- No retries on LLM failure in v1. A failure surfaces as a clear error; the accountant re-uploads. Retry policy is a §13 candidate if it comes up live.
- No background jobs. The pipeline runs inline in the request. Single user, embedded Postgres — this is fine.
- No Docker, no docker-compose.yml, no Dockerfile. `./dev.sh` is the README. Postgres runs embedded; the only prereqs are JDK 21, Node 20+, and pnpm.
- No Spring Security, no Actuator beyond `/health`, no observability stack. One user, local-only.
