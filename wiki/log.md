---
title: Log
description: Append-only chronological record of wiki operations
---

# Log

Append-only. Newest entries at the bottom (chronological). Each entry header is parseable: `grep "^## \[" log.md | tail -10` shows the most recent ten.

---

## [2026-04-27] schema | wiki bootstrapped
- Pages: [[CLAUDE]], [[index]], [[log]]
- Notes: Initial scaffolding. Directory layout, page conventions, ingest/query/lint workflows defined. Source scope set to "wide" (Claude sessions + project artifacts + git + external).

## [2026-04-27] ingest | interview brief (Invoice-to-Journal)
- Pages: [[interview-brief]], [[invoice-to-journal]]
- Notes: Original company brief. Task is PDF invoice → journal entry web app, React + TypeScript frontend, free choice of backend, Anthropic API key provided. Chart of accounts is BAS kontoplan subset, 20 accounts. Live interview phase introduces a harder PDF and a new feature.

## [2026-04-27] ingest | SPEC.md (Invoice-to-Journal)
- Pages: [[spec-invoice-to-journal]], [[invoice-to-journal]], [[llm-no-arithmetic]]
- Notes: Patrik's interpretation of the brief. Codifies that LLM never does arithmetic (§9.2), trust is surfaced via reasoning + confidence per mapping (§9.3), architecture must absorb live extensions without rewrites (§9.1). Twelve §11 anti-goals call out what NOT to build.

## [2026-04-27] ingest | PLAN.md (Invoice-to-Journal)
- Pages: [[plan-invoice-to-journal]], [[invoice-to-journal]], [[two-llm-calls-not-one]], [[sqlite-text-for-decimals]], [[bigdecimal-scale-equality]]
- Notes: Implementation plan. Java 21 + Spring Boot 3 backend, Vite + React + TS frontend, SQLite via better-sqlite3 + Flyway, two-call LLM pipeline (extract → validate → map → assemble → persist). Mapper chain pattern is the pivot for future supplier-rule shortcut. Two LLM calls (Anthropic), `cache_control: ephemeral` on the chart-of-accounts and system prompts.

## [2026-04-27] decision | invoice-to-journal: Postgres swap + Extractor seam
- Pages: [[postgres-numeric-for-decimals]], [[extractor-as-provider-seam]], [[llm-provider-portability]], [[sqlite-text-for-decimals]], [[plan-invoice-to-journal]], [[invoice-to-journal]], [[index]]
- Notes: PLAN.md evolved through three pivots in one session: (1) DB swap SQLite → Postgres 16 (Docker), money columns become `NUMERIC(18,2)` and the TEXT-encoding workaround is gone — old decision marked superseded. (2) `Extractor` interface added alongside `Mapper` so the pipeline holds no SDK references; symmetry restored. (3) Anthropic↔OpenAI portability captured as a concept page — prompts and JSON schemas port cheaply, multimodal PDF input is the asymmetry that bites; the architectural defense is project-shaped interfaces. Provider-swap row added to PLAN §11 live-extension table.

## [2026-04-27] decision | invoice-to-journal: drop Docker, use embedded-postgres
- Pages: [[postgres-numeric-for-decimals]], [[plan-invoice-to-journal]]
- Notes: Dev machine (MacBook 12") can't run Docker. Switched to `io.zonky.test:embedded-postgres` — real Postgres binary, started in-process, data persisted to `./data/pg/`. Zero new prereqs; first boot downloads ~30 MB. PLAN.md §2, §8, §12, §13 updated; `docker-compose.yml` and related dev.sh Docker step removed entirely.

## [2026-04-28] session | invoice-to-journal: Phase 1 complete
- Pages: [[invoice-to-journal]], [[embedded-postgres-clean-data-gotcha]]
- Notes: Built and verified Phase 1 foundation. Repo layout: `api/` (Spring Boot 3.5 + Gradle 8.14), `web/` (Vite + React + TS + Tailwind v4 + TanStack Query), root `dev.sh`. V1__init.sql created all 7 tables (including `debit XOR credit` CHECK constraint on `postings`). ChartSeeder seeds 20 BAS accounts idempotently on boot. PipelineService stub returns placeholder UUID; `POST /invoices` and `GET /health` wired. Exit check passed: clean boot, Flyway migration applied, `/health` 200, data persists across restarts. Key gotcha: `io.zonky.test:embedded-postgres` defaults to `setCleanDataDirectory(true)` which wipes the cluster on every start — must set `false` for production use; see [[embedded-postgres-clean-data-gotcha]]. Embedded Postgres binary pre-downloaded and cached (~30 MB, one-time). Phase 2 (pipeline core) is next.

## [2026-04-28] lint | wiki health check
- Pages: [[llm-no-arithmetic]], [[bigdecimal-scale-equality]], [[plan-invoice-to-journal]], [[invoice-to-journal]], [[index]]
- Notes: 2 broken wikilinks fixed (created missing concept pages). 1 contradiction fixed (TL;DR in plan-invoice-to-journal said "Postgres 16 in Docker" — updated to embedded-postgres, no Docker). Project page clarified: embedded-postgres:2.0.7 ships Postgres 14.10, not 16 as PLAN targeted. No orphan pages found.

## [2026-04-28] session | invoice-to-journal: Phase 2 — Validator, Assembler, PipelineService wired
- Pages: [[invoice-to-journal]]
- Notes: `Validator` implemented: checks `sum(lines.net) == netTotal` and `netTotal + vatTotal == grossTotal` using `compareTo` (correct — avoids BigDecimal scale traps, see [[bigdecimal-scale-equality]]). `Assembler` implemented: debits each line's net to the LLM-chosen cost account, debits `vatTotal` to account 2640 (Ingående moms), credits `grossTotal` to account 2440 (Leverantörsskuld); asserts debits == credits as a final defence. `Posting.confidence` changed from primitive `double` to boxed `Double` to allow null on the two deterministic synthetic postings (VAT and AP rows carry no LLM confidence). `PipelineService` wired: extractor → validator → mapper chain (first non-empty Optional wins) → assembler → placeholder UUID return. Pipeline will throw `UnsupportedOperationException` at the extractor call until `AnthropicExtractor` is added to `infrastructure/llm/`. API key handling also wired: `dev.sh` bootstraps `api/.env` from the out-of-band key file on first run; `.env` is gitignored.

## [2026-04-28] refactor | invoice-to-journal: domain layer introduced
- Pages: [[domain-layer-introduction]], [[invoice-to-journal]], [[extractor-as-provider-seam]], [[index]]
- Notes: Four-layer package structure established before Phase 2 business logic. New packages: `domain/model/` (Money, Account, InvoiceLine, ExtractedInvoice, MappingProposal, Posting, SuggestionId), `domain/port/` (Extractor, Mapper), `domain/service/` (Validator, Assembler stubs), `domain/exception/` (ExtractionException, ValidationException), `application/` (PipelineService), `infrastructure/config/` and `infrastructure/seed/`. Old `pipeline/`, `config/`, `seed/` packages removed. InvoiceController imports updated. Compiles clean. PLAN.md §1 updated with package diagram and dependency rules.

## [2026-04-29] session | invoice-to-journal: eval harness complete — all 3 fixtures green
- Pages: [[invoice-to-journal]], [[plan-invoice-to-journal]]
- Notes: `PipelineService.evaluate()` added (runs pipeline without persisting, returns `EvalResult` with extracted invoice, postings, latency). `EvalRunner` updated to load `{name}.expected.json`, compare mapped account codes per line, and print the PLAN §10 table. Three fixtures with expected JSON: `sample.pdf` (3 lines → 6540/6540/6550), `rent.pdf` (→ 5010), `lunch.pdf` (→ 7731). Actual results: lunch 1/1 @ 0.95, rent 1/1 @ 0.99, sample 3/3 @ 0.95 — all PASS. `PostgresConfig` and `ChartSeeder` gated `@Profile("!eval")`; `application-eval.yml` disables DataSource/Flyway autoconfiguration so eval runs without Postgres. Fixture PDFs (`rent.pdf`, `lunch.pdf`) generated with `reportlab` — properly embedded text, readable by Claude's vision parser. Phase 3 (persistence + API) is next.

## [2026-04-29] session | invoice-to-journal: Phase 2 complete — LLM pipeline live
- Pages: [[invoice-to-journal]], [[two-llm-calls-not-one]], [[extractor-as-provider-seam]]
- Notes: `AnthropicExtractor` implemented in `infrastructure/llm/` — sends PDF as base64 document block with forced tool-use (`extract_invoice`); parses JSON response via Jackson `valueToTree` (key fix: `JsonValue.toString()` is not valid JSON). `AnthropicMapper` implemented — one Haiku call per line with chart-of-accounts in cached system prompt (`cache_control: ephemeral`); forces `map_line` tool. `AnthropicConfig` wires `AnthropicClient` bean from `ANTHROPIC_API_KEY` env var. `PipelineService` fully wired: extract → validate → map chain → assemble → placeholder UUID (Phase 3 adds DB persist). `ApiExceptionHandler` maps `ValidationException` and `ExtractionException` to HTTP 422. `EvalRunner` boots a headless Spring context (`WebApplicationType.NONE`), iterates all PDFs in `src/eval/fixtures/`, prints PASS/FAIL per file. `sample.pdf` → PASS; synthetic `rent.pdf`/`lunch.pdf` → FAIL (Claude cannot read PDFs generated by raw PDF writer — needs real PDFs with properly rendered text). eval Gradle task fixed to use Java 21 toolchain explicitly. Phase 3 (persist + API) is next.

## [2026-04-28] session | invoice-to-journal: pre-Phase-3 review + simplify pass
- Pages: [[invoice-to-journal]]
- Notes: Reviewed Phase 1–2 work end-to-end against [[plan-invoice-to-journal]]; ran `dev.sh` and verified `POST /invoices` returns 200 in ~10s on `sample.pdf` (full extract → validate → map → assemble round-trip). README rewritten with prerequisites, run instructions, and current endpoint coverage. Build fix: `springBoot { mainClass = "com.sturdywaffle.ApiApplication" }` pinned in `build.gradle.kts` because `EvalRunner` is a second `@SpringBootApplication` on the classpath and Gradle couldn't pick a default. Then a `/simplify` review (three parallel agents on reuse / quality / efficiency) identified ten cleanups, all applied: (1) deleted broken `ApiApplicationTests.java` (imports of non-existent `com.sturdywaffle.pipeline.*`, no-arg ctor against the real two-arg ctor — `./gradlew test` previously failed silently because `bootRun` skips tests, now passes); (2) dropped redundant `@Component` from `EvalRunner` (`@SpringBootApplication` already meta-implies it; kept `@Profile("eval")` because component-scan picks up EvalRunner during normal HTTP boot otherwise); (3) deleted dead `anthropic.api-key` block from `application.yml` (`AnthropicConfig` uses `@Value("${ANTHROPIC_API_KEY}")` directly); (4) deleted "Phase 3: persist…" narration comment in `PipelineService`; (5) `Money` got a compact constructor that always canonicalizes to scale 2 — removes the `new Money(bd)` footgun where the record default `equals` would diverge from `compareTo`; (6) `Validator` rewritten to operate on `Money` end-to-end (`Money.add` + `.equals`) instead of leaking to `BigDecimal.compareTo`; (7) `AnthropicConfig` sets `.timeout(Duration.ofSeconds(30))` — SDK default was 10 minutes per request, a hung call could block a thread that long; (8) replaced `mapper.valueToTree(toolUse._input())` with `toolUse._input().convert(JsonNode.class)` in both `AnthropicExtractor` and `AnthropicMapper` (SDK `JsonValue.convert(Class<R>)` skips a Jackson tree round-trip; `ObjectMapper` field deleted from both); (9) `AnthropicMapper.buildChartPrompt` uses `ClassPathResource.getContentAsString(StandardCharsets.UTF_8)` — was `getInputStream().readAllBytes()` + platform-default `new String(bytes)`; (10) `ChartSeeder` switched to `ClassPathResource` for consistency. Smoke-tested post-restart: `sample.pdf` → 200 in ~10s, same as before. Deferred from /simplify (deliberate, not bugs): (a) parallelize per-line mapper calls — needs a "warm cache then fan out" pattern so calls 2..N hit the ephemeral chart-prompt cache, biggest latency win available but architectural; (b) lift hardcoded model names (`claude-sonnet-4-6`, `claude-haiku-4-5`) to `application.yml` for A/B without recompile. Plan deviation noted: `AnthropicMapper` uses `claude-haiku-4-5`, but [[plan-invoice-to-journal]] §2 still commits to sonnet-4-6 for both calls — plan should be updated to reflect the Haiku swap (sensible cost/latency choice for constrained tool-use mapping) rather than reverting the code.
