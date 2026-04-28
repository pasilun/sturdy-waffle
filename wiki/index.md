---
title: Index
description: Catalog of all pages in the wiki, organized by type
---

# Index

The catalog of everything in this wiki. The LLM updates this on every ingest. Pages are listed with a one-line hook so the right one can be picked without opening it.

## Projects

- [[invoice-to-journal]] — Take-home interview: PDF invoice → suggested journal entry, accountant approves/declines. Status: active.

## Concepts

- [[llm-no-arithmetic]] — Principle: LLMs handle judgment (mapping, classification); deterministic code handles arithmetic (balancing, validation).
- [[bigdecimal-scale-equality]] — Java gotcha: `BigDecimal.equals` is scale-sensitive (`1.20 ≠ 1.2`); canonicalize on parse with `Money.of()`, then `.equals` is safe.
- [[llm-provider-portability]] — Anthropic↔OpenAI: prompts and JSON schemas port cheaply; multimodal inputs (PDFs, images) are the expensive asymmetry. Defense: project-shaped interfaces.
- [[embedded-postgres-clean-data-gotcha]] — `io.zonky.test:embedded-postgres` reinitializes the cluster on every boot by default; `setCleanDataDirectory(false)` required for state to persist.

## Decisions

- [[two-llm-calls-not-one]] — Invoice-to-Journal: extraction and mapping run as two separate LLM calls, not one combined call.
- [[postgres-numeric-for-decimals]] — Invoice-to-Journal: monetary amounts stored as `NUMERIC(18,2)` in Postgres. Supersedes the SQLite TEXT decision.
- [[extractor-as-provider-seam]] — Invoice-to-Journal: pipeline depends on an `Extractor` interface, not the Anthropic SDK; symmetric with `Mapper`.
- [[domain-layer-introduction]] — Invoice-to-Journal: four-layer package structure (`domain/`, `application/`, `infrastructure/`, `web/`); domain services testable without Spring.
- [[sqlite-text-for-decimals]] — _superseded_ by [[postgres-numeric-for-decimals]]. Kept for the SQLite-specific rationale.

## Sessions

_(none yet — Claude Code session summaries land here)_

## Sources

- [[interview-brief]] — Original take-home brief from the company: task description, chart of accounts, live-interview format.
- [[spec-invoice-to-journal]] — `~/src/SPEC.md` — what the system must do (problem, requirements, anti-goals, resolved decisions).
- [[plan-invoice-to-journal]] — `~/src/PLAN.md` — how the system will be built (architecture, stack, pipeline, mapper chain, eval harness).
