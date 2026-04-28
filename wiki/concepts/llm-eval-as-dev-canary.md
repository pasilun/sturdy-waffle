---
title: LLM eval harness as dev canary
type: concept
created: 2026-04-28
updated: 2026-04-28
tags: [llm, eval, testing, dev-loop, prompt-engineering]
---

# LLM eval harness as dev canary

**An eval harness is not a unit test suite. It's a small, intentional regression check you run before and after risky changes to LLM-shaped code.**

A useful eval harness has a different shape from JUnit-style tests:
- It hits the real model API (real tokens, real latency)
- It's run on demand, not on every save
- The pass/fail is "did mapping accuracy regress" not "did this function return the right value"

## The three properties that make one useful

**1. Same code path as production.** The harness must call the same orchestration code that the HTTP handler calls. If the eval has its own parallel "what we think the pipeline does" logic, it drifts and stops catching real regressions. In Invoice-to-Journal, `EvalRunner` calls `PipelineService.evaluate(pdf)` which shares a private `process()` with `run()` — extraction, validation, mapping, assembly are identical; only persistence is skipped.

**2. Fixtures pin expected behavior, not just shape.** Each fixture is `{name}.pdf` + `{name}.expected.json` with the expected output. Without expected values, the harness only catches crashes — not "the model now picks 6210 instead of 6230 for mobile telecoms after a prompt edit."

**3. Boundary cases on purpose.** At least one fixture should sit on a category boundary the LLM is likely to flip on. In Invoice-to-Journal that's `lunch.pdf` (staff meals 7731 vs entertainment) — its job is to surface confidence-calibration drift, not to be easy to pass.

## What it buys you in the dev loop

| Trigger | What eval tells you |
|---|---|
| Edited a prompt (`extract.v1.txt`, `map.v1.txt`) | Did accuracy or confidence drop on any fixture? |
| Swapped a model (Sonnet → Haiku, etc.) | Latency / accuracy / confidence delta per fixture |
| Refactored the pipeline | Pre-persistence logic still intact (eval skips DB) |
| New "harder PDF" arrives (e.g. live-interview ask) | Drop into `fixtures/`, write expected JSON, rerun |
| Suspect over-confidence | `lunch.pdf` confidence avg is the canary |

## What it does *not* do

- It's not free. Each run hits the real API. Don't put it on `pre-commit`.
- It's not exhaustive. Three fixtures is enough to catch obvious regressions, not edge-case behavior.
- It's not a substitute for unit tests on deterministic code (validator, assembler, money type).

The right mental model: eval is the **smoke test before you ship a prompt or model change**, plus the **safety net while refactoring around the LLM**. Unit tests handle the deterministic core; eval handles the probabilistic edges.

## See also

- [[invoice-to-journal]] — project where this harness pattern is applied
- [[plan-invoice-to-journal]] §10 — the eval table format and fixture conventions
- [[llm-no-arithmetic]] — the eval harness is the right place to catch *judgment* regressions; deterministic balance checks belong in code, not eval
