---
title: Index
description: Catalog of all pages in the wiki, organized by type
---

# Index

The catalog of everything in this wiki. The LLM updates this on every ingest. Pages are listed with a one-line hook so the right one can be picked without opening it.

## Projects

- [[invoice-to-journal]] — Take-home interview: PDF invoice → suggested journal entry, accountant approves/declines. Status: active.

## Concepts

- [[mcp-browser-driven-ui-review]] — Methodology: autonomous workflow for using the Playwright MCP browser to drive the running app, compare to spec, and produce an ordered bugs/gaps/polish list. Phase-end checkpoint.
- [[llm-no-arithmetic]] — Principle: LLMs handle judgment (mapping, classification); deterministic code handles arithmetic (balancing, validation).
- [[bigdecimal-scale-equality]] — Java gotcha: `BigDecimal.equals` is scale-sensitive (`1.20 ≠ 1.2`); canonicalize on parse with `Money.of()`, then `.equals` is safe.
- [[llm-provider-portability]] — Anthropic↔OpenAI: prompts and JSON schemas port cheaply; multimodal inputs (PDFs, images) are the expensive asymmetry. Defense: project-shaped interfaces.
- [[embedded-postgres-clean-data-gotcha]] — `io.zonky.test:embedded-postgres` reinitializes the cluster on every boot by default; `setCleanDataDirectory(false)` required for state to persist.
- [[llm-eval-as-dev-canary]] — Eval harness is a regression canary on prompt/model changes, not a unit test suite. Same code path as production, fixtures with expected outputs, boundary cases for calibration drift.
- [[side-effects-in-transactional-methods]] — File IO / network calls inside `@Transactional` widen the tx and create dual-write inconsistencies. Split into a non-transactional method + a transactional one on the same bean.
- [[interface-default-as-silent-lie]] — `default String modelId() { return "unknown"; }` is a silent lie waiting to happen. Force compiler-enforced overrides for any value that lands in an audit log or DB column.
- [[tests-protect-invariants-not-implementation]] — Smart tests assert what the spec promises, not how the code happens to do it. The four-question filter for tests that earn rent vs. tests that rot.

## Decisions

- [[two-llm-calls-not-one]] — Invoice-to-Journal: extraction and mapping run as two separate LLM calls, not one combined call.
- [[postgres-numeric-for-decimals]] — Invoice-to-Journal: monetary amounts stored as `NUMERIC(18,2)` in Postgres. Supersedes the SQLite TEXT decision.
- [[extractor-as-provider-seam]] — Invoice-to-Journal: pipeline depends on an `Extractor` interface, not the Anthropic SDK; symmetric with `Mapper`.
- [[domain-layer-introduction]] — Invoice-to-Journal: four-layer package structure (`domain/`, `application/`, `infrastructure/`, `web/`); domain services testable without Spring.
- [[sqlite-text-for-decimals]] — _superseded_ by [[postgres-numeric-for-decimals]]. Kept for the SQLite-specific rationale.
- [[switch-provider-anthropic-to-openai]] — Invoice-to-Journal: swap Anthropic SDK for OpenAI SDK due to billing outage; PDFBox for PDF text extraction; gpt-4o / gpt-4o-mini model mapping.

## Sessions

- [[2026-04-29-phase-5-multi-page-shell-plan]] — Invoice-to-Journal Phase 5 plan: sidebar nav, invoices list, accounts page, activity feed.
- [[2026-04-29-phase-6-playwright-plan]] — Invoice-to-Journal Phase 6 plan: Playwright (mocked + live tiers), husky hooks, MCP browser driving.
- [[2026-04-29-first-mcp-browser-review]] — First MCP browser-driven UI review of Invoice-to-Journal: 1 bug (NaN% on null confidence) + 6 polish items + spec compliance check.
- [[2026-04-29-mapping-escalation-plan]] — Invoice-to-Journal: design for "Escalate mapping" button — re-run mapping with stronger model, locked once approved, replace not version.
- [[2026-04-29-openai-migration-plan]] — Invoice-to-Journal: step-by-step plan to swap Anthropic SDK for OpenAI SDK (billing outage trigger); 12 files, PDF via PDFBox text extraction.

## Sources

- [[interview-brief]] — Original take-home brief from the company: task description, chart of accounts, live-interview format.
- [[spec-invoice-to-journal]] — `~/src/SPEC.md` — what the system must do (problem, requirements, anti-goals, resolved decisions).
- [[plan-invoice-to-journal]] — `~/src/PLAN.md` — how the system will be built (architecture, stack, pipeline, mapper chain, eval harness).
