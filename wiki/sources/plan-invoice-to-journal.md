---
title: PLAN.md — Invoice-to-Journal
type: source
source_path: ../../PLAN.md
ingested: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
tags: [plan, invoice-to-journal, architecture, postgres, provider-seams]
---

# PLAN.md — Invoice-to-Journal

Implementation plan. Companion to [[spec-invoice-to-journal]]. Every decision traces to a SPEC section.

## TL;DR

Java 21 + Spring Boot 3 + Postgres via `io.zonky.test:embedded-postgres` (no Docker — real Postgres binary, in-process, data at `api/data/pg/`) + `NUMERIC(18,2)` for decimals (see [[postgres-numeric-for-decimals]]) + Flyway + Vite/React/TS. Single Spring `@Service` runs an inline pipeline: store → extract → validate → map → assemble → persist. Two LLM calls only (extract, map), both behind project-shaped interfaces (`Extractor`, `Mapper`) so the pipeline holds no SDK references — see [[extractor-as-provider-seam]]. v1 uses Anthropic; provider swap (e.g. OpenAI) is a 2-class change with the multimodal asymmetry contained inside the new `Extractor` (see [[llm-provider-portability]]). Three golden-case fixtures gate prompt edits via `./gradlew :api:eval`.

## Three principles (priority order)

1. The LLM never does arithmetic. Mapping is judgment; balancing is code. — [[llm-no-arithmetic]]
2. Every layer is replaceable. Mapper, extractor, store, UI all behind interfaces.
3. Trust is surfaced, not asserted. Reasoning + confidence ride alongside every mapping decision into the UI.

## Stack

| Layer | Choice | Why |
|---|---|---|
| Frontend | React + TS + Vite | SPEC mandate (§4.6); Vite for fast dev loop |
| Styling | Tailwind | Zero cycles on CSS naming |
| Backend | Java 21 + Spring Boot 3 | LTS Java; SB is path-of-least-resistance for single-user CRUD+LLM |
| API build | Gradle (Kotlin DSL) | Daemon keeps incremental builds snappy |
| HTTP | Spring Web MVC | Synchronous fits inline pipeline; WebFlux overkill |
| DB | Postgres 16 (`org.postgresql:postgresql`) via `io.zonky.test:embedded-postgres` + Spring Data JDBC + Flyway | Native `NUMERIC(18,2)`; no Docker, no daemon. Downloads real Postgres binary ~30 MB on first boot. See [[postgres-numeric-for-decimals]] |
| Decimals | `BigDecimal` scale 2 + `Money` value type | See [[bigdecimal-scale-equality]] |
| LLM SDK (v1) | `com.anthropic:anthropic-java` | Native PDF document blocks; tool-use; `cache_control` for prompt caching. Held behind `Extractor` / `Mapper` interfaces, not imported by the pipeline — see [[extractor-as-provider-seam]] |
| Model | `claude-sonnet-4-6` for both calls | 15s budget fits; swap to Opus 4.7 for the live harder PDF |
| Tests | JUnit 5 + AssertJ | Unit tests on validator + assembler + mapper chain |
| Layout | `web/` (pnpm) + `api/` (Gradle) + `dev.sh` | Two languages → two build systems; don't unify |

## Data model (key tables)

- `accounts` — chart, seeded
- `invoices` — supplier_name, invoice_number, invoice_date, currency, net, vat, gross, pdf_path
- `extractions` — raw_json, model, prompt_version, latency_ms
- `suggestions` — model, prompt_version, latency_ms (per-mapping-call)
- `postings` — line_index, account_code FK, debit, credit, description, **reasoning, confidence**
- `decisions` — UNIQUE FK on suggestion_id, status, decided_at
- `audit_events` — entity, event, payload_json — unused in v1 code paths but the table exists, so the audit-log feature is a write not a migration

Money columns are `NUMERIC(18,2)` — see [[postgres-numeric-for-decimals]] (supersedes [[sqlite-text-for-decimals]]).

## Pipeline (`PipelineService.run(byte[] pdf)`)

1. **Store** — PDF to `data/uploads/<uuid>.pdf`, insert `invoices` stub.
2. **Extract** — call the configured `Extractor` bean (v1: `AnthropicExtractor`, PDF as document block + tool-use schema). Extracts supplier, number, date, currency, lines, totals. No reasoning/confidence (extraction is mechanical). Pipeline holds no SDK reference — see [[extractor-as-provider-seam]].
3. **Validate** — parse all amounts to `BigDecimal` scale 2. Assert `sum(lines.net).equals(net_total)` and `net_total.add(vat_total).equals(gross_total)`. Failure → typed `ExtractionException` → 422 with the specific mismatch.
4. **Map** — mapper chain over each line. Each line emits `{ account_code, reasoning, confidence }`. VAT-in (2640) and supplier-liability (2440) are deterministic, not LLM-chosen.
5. **Assemble** — debit cost account per line, debit VAT-in for `vat_total`, credit supplier-liability for `gross_total`. Assert debits == credits (defense-in-depth; tautological after step 3).
6. **Persist** — one `@Transactional` boundary, write `extractions` + `suggestions` + `postings`, emit audit event.

Steps 2 and 4 are the only LLM calls. Steps 3, 5, 6 are pure code.

## LLM design

Two prompts, two output schemas, both versioned files under `api/src/main/resources/prompts/` (`extract.v1.txt`, `map.v1.txt`). Prompt version persisted on every row so quality regressions correlate to edits.

- **Extraction**: PDF in (vision/document block on Anthropic), strict JSON via the provider's structured-output mechanism (tool-use on Anthropic, `response_format: json_schema` on OpenAI). Refuses to invent missing fields — null + validator handles it.
- **Mapping**: extracted lines + full chart (20 rows) + supplier name. Per line: `{ account_code, reasoning ≤140 chars, confidence 0–1 }`. Schema enforces enum on `account_code`.
- **Caching**: Anthropic uses explicit `cache_control: ephemeral` on chart + system prompt; OpenAI caches automatically above ~1024 tokens (see [[llm-provider-portability]]). ~hundreds of ms saved on warm runs.

## Provider seams (§6)

Two interfaces, both provider-agnostic. The pipeline depends on these, not on any SDK.

```java
public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
}

public interface Mapper {
    Optional<MappingProposal> map(Invoice invoice, Line line);
}
```

v1 wiring: `AnthropicExtractor` (single bean) + `[LlmMapper]` (chain). Two pivot points:

- **Provider swap** (e.g. OpenAI) — add `OpenAiExtractor` and `OpenAiLlmMapper`, switch beans via `@ConditionalOnProperty("llm.provider")`. The multimodal-input asymmetry (Anthropic accepts PDFs natively; OpenAI rasterizes or uses Files API) lives entirely inside the new `Extractor` impl. See [[extractor-as-provider-seam]] and [[llm-provider-portability]].
- **Supplier-preference register** — prepend `SupplierRuleMapper` to the mapper chain. One new `@Component` + one config line.

## Numeric handling

See [[bigdecimal-scale-equality]] for the gotcha. `Money` value type wraps `BigDecimal`, canonicalizes to scale 2 at the boundary, then `.equals` is safe. Storage round-trip via Spring `Converter<Money, BigDecimal>` pair (Postgres column is `NUMERIC(18,2)` — no string encoding). Single currency per invoice in v1.

## Frontend

Two routes:
- `/` — drag-drop PDF upload; on success navigate to review.
- `/invoices/:id` — split view. Left: PDF in `<iframe>` (browsers render natively, no `react-pdf` dep). Right: postings table; reasoning inline as muted line under account name; confidence as colored bar (green ≥0.8, amber 0.5–0.8, red <0.5). Approve / Decline buttons.

State: TanStack Query. No global store. No router beyond two routes.

## Eval harness

`./gradlew :api:eval` → `EvalRunner` `@Component` under `eval` Spring profile. Loads the same `PipelineService` bean as HTTP (no parallel codepath). Three fixtures: sample, rent invoice (single line, "rent" account), staff lunch (single line, "staff meals" — boundary case with entertainment, lower confidence expected). Output is a per-case table: extract pass/fail, map @ 1, average confidence, latency.

This is the canary for every prompt edit and model swap.

## Live-extension defense table (§11)

Mirrored in [[invoice-to-journal]] under "Live-extension defenses". Six extensions, each with a prebuilt seam: multi-page PDF, **provider swap**, supplier register, audit log, line-to-many splits, edit-before-approve. If a live ask doesn't fit, that's a gap — flag, don't wing.

## Build order (§12)

Each step is a green checkpoint; no step N+1 if N is red.
1. Repo skeleton + Flyway + chart seed + `PipelineService` stub.
2. Extraction call + `Money` + validator. First fixture green.
3. Mapper chain (LLM-only) + assembler. All three fixtures green.
4. Persistence + decision endpoint. cURL round-trip works.
5. Frontend: upload → review → approve/decline.
6. README.
7. Cold-clone smoke test.

## Deliberately not built (§13)

- No streaming (15s budget is comfortable for non-streamed tool-use).
- No retries on LLM failure (re-upload is fine for v1).
- No background jobs (inline pipeline is fine for one user).
- No Docker at all. `./dev.sh` is the README. Postgres starts embedded; prereqs are JDK 21, Node 20+, pnpm.
- No Spring Security, Actuator beyond `/health`, observability stack.

## See also

- [[invoice-to-journal]] — project page
- [[spec-invoice-to-journal]] — what this plan implements
- [[two-llm-calls-not-one]] — the extract-then-map decision
- [[postgres-numeric-for-decimals]] — current storage decision
- [[sqlite-text-for-decimals]] — superseded predecessor; rationale still applies if SQLite returns
- [[extractor-as-provider-seam]] — the §6 interface decision
- [[llm-provider-portability]] — generalizable concept behind the provider seam
- [[bigdecimal-scale-equality]] — the decimal gotcha this plan defends against
- [[llm-no-arithmetic]] — the principle driving step boundaries in the pipeline
