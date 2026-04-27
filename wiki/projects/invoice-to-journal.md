---
title: Invoice-to-Journal Entry
type: project
status: active
project_path: ../..
created: 2026-04-27
updated: 2026-04-27
tags: [interview, llm, accounting, java, react, postgres]
---

# Invoice-to-Journal Entry

Take-home interview project. A web app where an accountant uploads a PDF invoice and the system proposes a balanced double-entry journal entry against a fixed Swedish chart of accounts (BAS kontoplan subset). The accountant approves or declines.

## Status

Spec and plan are written ([[spec-invoice-to-journal]], [[plan-invoice-to-journal]]). Implementation has not started in this directory yet — `~/src/` currently contains only the planning artifacts and the original brief in `interview 2/`.

## Why this exists

Live interview test of how the candidate uses AI tools to build a real product. Grading is on product experience, not accounting perfection — see [[interview-brief]] for the original framing and [[spec-invoice-to-journal]] §8 for the explicit de-prioritizations.

## Architecture (one paragraph)

Spring Boot 3 + Java 21 backend, Vite + React + TypeScript frontend, Postgres 16 in Docker via Spring Data JDBC + Flyway. Single Spring `@Service` runs the inline pipeline: store PDF → call configured `Extractor` (Anthropic in v1, with PDF document block + tool-use) → validate `net + vat == gross` strictly → map each line via mapper chain (LLM-only in v1, supplier-rule shortcut later) → assemble balanced postings → persist in one transaction. Two LLM calls only (extract, map); see [[two-llm-calls-not-one]]. Both calls sit behind project-shaped interfaces — see [[extractor-as-provider-seam]]. Arithmetic is always code, never LLM — see [[llm-no-arithmetic]].

## Key design decisions

- [[two-llm-calls-not-one]] — extraction and mapping are separate calls.
- [[postgres-numeric-for-decimals]] — money is `NUMERIC(18,2)` in Postgres; supersedes [[sqlite-text-for-decimals]].
- [[extractor-as-provider-seam]] — pipeline holds an `Extractor` interface, not an SDK; symmetric with `Mapper`. Concretizes [[llm-provider-portability]].
- Confidence and reasoning are attached to mappings only, not extracted numbers ([[spec-invoice-to-journal]] §4.2).
- Edit-before-approve is deferred to v2; data model accommodates it via FK on `account_code`.

## Live-extension defenses

The architecture is designed to absorb specific extensions without rewrites. From [[plan-invoice-to-journal]] §11:

| Extension | How it lands |
|---|---|
| Multi-page / missing-field PDF | New `extract.v2.txt` prompt; bump version constant |
| Provider swap (OpenAI, etc.) | Two new `@Component`s (`Extractor` + `Mapper` impls) + `llm.provider` switch. Multimodal-input cost stays inside the new `Extractor` |
| Supplier-preference register | New `Mapper` `@Component`; one line in chain config |
| Audit log | Frontend page reads existing `audit_events` table |
| One-line-to-many splits | Mapping schema gains `splits[]`; assembler unchanged |
| Edit-before-approve | Frontend-only: editable cell + PATCH endpoint |

If a live ask doesn't fit any row, that's a design gap — flag, don't wing it.

## Sources

- [[interview-brief]] — original company brief
- [[spec-invoice-to-journal]] — what the system must do
- [[plan-invoice-to-journal]] — how it will be built

## Open questions / things to track

- Implementation hasn't started in `~/src/` — there's no `web/` or `api/` directory yet.
- An Anthropic API key is stored at `~/src/interview 2/anthropic_api_key.txt` (out-of-band credential — do not read or commit).
