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

## [2026-04-27] decision | invoice-to-journal: drop Docker, use embedded-postgres
- Pages: [[postgres-numeric-for-decimals]], [[plan-invoice-to-journal]]
- Notes: Dev machine (MacBook 12") can't run Docker. Switched to `io.zonky.test:embedded-postgres` — real Postgres binary, started in-process, data persisted to `./data/pg/`. Zero new prereqs; first boot downloads ~30 MB. PLAN.md §2, §8, §12, §13 updated; `docker-compose.yml` and related dev.sh Docker step removed entirely.

## [2026-04-28] session | invoice-to-journal: Phase 1 complete
- Pages: [[invoice-to-journal]], [[embedded-postgres-clean-data-gotcha]]
- Notes: Built and verified Phase 1 foundation. Repo layout: `api/` (Spring Boot 3.5 + Gradle 8.14), `web/` (Vite + React + TS + Tailwind v4 + TanStack Query), root `dev.sh`. V1__init.sql created all 7 tables (including `debit XOR credit` CHECK constraint on `postings`). ChartSeeder seeds 20 BAS accounts idempotently on boot. PipelineService stub returns placeholder UUID; `POST /invoices` and `GET /health` wired. Exit check passed: clean boot, Flyway migration applied, `/health` 200, data persists across restarts. Key gotcha: `io.zonky.test:embedded-postgres` defaults to `setCleanDataDirectory(true)` which wipes the cluster on every start — must set `false` for production use; see [[embedded-postgres-clean-data-gotcha]]. Embedded Postgres binary pre-downloaded and cached (~30 MB, one-time). Phase 2 (pipeline core) is next.

## [2026-04-27] decision | invoice-to-journal: Postgres swap + Extractor seam
- Pages: [[postgres-numeric-for-decimals]], [[extractor-as-provider-seam]], [[llm-provider-portability]], [[sqlite-text-for-decimals]], [[plan-invoice-to-journal]], [[invoice-to-journal]], [[index]]
- Notes: PLAN.md evolved through three pivots in one session: (1) DB swap SQLite → Postgres 16 (Docker), money columns become `NUMERIC(18,2)` and the TEXT-encoding workaround is gone — old decision marked superseded. (2) `Extractor` interface added alongside `Mapper` so the pipeline holds no SDK references; symmetry restored. (3) Anthropic↔OpenAI portability captured as a concept page — prompts and JSON schemas port cheaply, multimodal PDF input is the asymmetry that bites; the architectural defense is project-shaped interfaces. Provider-swap row added to PLAN §11 live-extension table.
