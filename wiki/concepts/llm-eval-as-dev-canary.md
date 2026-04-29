---
title: LLM eval harness as dev canary
type: concept
created: 2026-04-28
updated: 2026-04-28
tags: [llm, eval, testing, dev-loop, prompt-engineering, model-selection]
---
s
# LLM eval harness as dev canary

**An eval harness is not a unit test suite. It's a small, intentional regression check you run before and after risky changes to LLM-shaped code.**

A useful eval harness has a different shape from JUnit-style tests:
- It hits the real model API (real tokens, real latency)
- It's run on demand, not on every save
- The pass/fail is "did mapping accuracy regress" not "did this function return the right value"

## The three properties that make one useful

**1. Same code path as production.** The harness must call the same orchestration code that the HTTP handler calls. If the eval has its own parallel "what we think the pipeline does" logic, it drifts and stops catching real regressions. In Invoice-to-Journal, `EvalRunner` calls `PipelineService.evaluate(pdf)` which shares a private `process()` with `run()` — extraction, validation, mapping, assembly are identical; only persistence is skipped.

**2. Fixtures pin expected behavior, not just shape.** Each fixture is `{name}.pdf` + `{name}.expected.json` with the expected output. Without expected values, the harness only catches crashes — not "the model now picks 6210 instead of 6230 for mobile telecoms after a prompt edit."

**3. Boundary cases on purpose.** At least one fixture should sit on a category boundary the LLM is likely to flip on. In Invoice-to-Journal that's `lunch.pdf` (staff meals 7631 vs entertainment) — its job is to surface confidence-calibration drift, not to be easy to pass.

## What it buys you in the dev loop

| Trigger | What eval tells you |
|---|---|
| Edited a prompt (`extract.v1.txt`, `map.v1.txt`) | Did accuracy or confidence drop on any fixture? |
| Swapped a model (Sonnet → Haiku, etc.) | Latency / accuracy / confidence delta per fixture |
| Refactored the pipeline | Pre-persistence logic still intact (eval skips DB) |
| New "harder PDF" arrives (e.g. live-interview ask) | Drop into `fixtures/`, write expected JSON, rerun |
| Suspect over-confidence | `lunch.pdf` confidence avg is the canary |

## Tuning model choice per feature

The strongest use of this harness is per-feature model selection — when a pipeline has multiple LLM calls (in [[invoice-to-journal]]: extraction + mapping), the harness can tell you which model is overkill where. Two reasons it's well-shaped for this:

- **Each feature has a distinct success signal.** Extraction is mechanical — its pass/fail is the validator (`net + vat == gross`, line sum matches). Mapping is judgment — its pass/fail is the per-fixture accuracy ratio (e.g. `lunch.pdf 1/1`). Different features get measured differently in the same run.
- **The boundary fixture surfaces calibration drift first.** A cheaper model often still gets the easy fixtures right but starts overclaiming on the close calls. `lunch.pdf` (staff meals 7631 vs entertainment) is the canary: if accuracy holds *and* confidence stays calibrated (not 0.95 on a wrong answer), the cheaper model is viable. Overconfidence on a wrong mapping is worse than under-confidence — it misleads the accountant's "do I need to look closely" cue.

Suggested experiment matrix (one constant change + `./gradlew :api:eval` per cell):

| | Extractor | Mapper | What to watch |
|---|---|---|---|
| Status quo | sonnet-4-6 | haiku-4-5 | baseline |
| Cheap extract | **haiku-4-5** | haiku-4-5 | does validator still pass? big cost win if yes |
| Smart map | sonnet-4-6 | **sonnet-4-6** | does `lunch.pdf` accuracy hold and confidence stay calibrated? |
| Bottom | haiku-4-5 | haiku-4-5 | the floor |
| Top | opus-4-7 | sonnet-4-6 | the ceiling — useful as a prep target for a harder live PDF |

### Caveats specific to model selection

1. **Three fixtures is statistically thin.** A model that's slightly worse may still happen to pass all three. Eval gives "definitely worse" signal, not "definitely good enough." Add fixtures (foreign-currency, multi-line restaurant receipt, sloppy scan) before trusting a cheaper model in front of a real user.
2. **Non-determinism.** Same fixture + same model can produce different confidences and occasionally different account picks across runs. To reduce noise: run each cell ≥3 times and look at spread, or set `temperature: 0` in `MessageCreateParams` for deterministic outputs.
3. **Hardcoded model constants.** Today the model lives as `private static final String MODEL` in each `Extractor` / `Mapper` impl — every swap is an edit + recompile. Lifting to `application.yml` (`anthropic.extractor.model`, `anthropic.mapper.model`) makes the matrix runnable from a config or env var instead, which is the prerequisite for scripting a real sweep.

### Beyond fixtures: production becomes a second observational study

With Phase 3 persistence in place, every `extractions` and `suggestions` row carries `model` and `prompt_version` — recorded via the `Extractor.modelId()` / `Mapper.modelId()` / `promptVersion()` port methods so the pipeline never leaks provider details. That means the eval harness stops being the only source of "which model worked when" — the database accumulates real-world cases and accountant decisions over time. Eval gives you fast pre-ship signal across a tiny matrix; production gives you slow long-tail signal across the messy real distribution. They cover different gaps and you want both.

This also opens **dynamic model escalation** as a near-term option: if a Mapper returns confidence < threshold, escalate to a stronger model and retry. The eval harness is where you'd find the right threshold first — what does `lunch.pdf` confidence look like at Haiku vs Sonnet? — before turning it on in production. And once it's on, the persisted `model` column lets you measure how often escalation fires, which is the feedback loop that tells you whether your threshold is right.

## What it does *not* do

- It's not free. Each run hits the real API. Don't put it on `pre-commit`.
- It's not exhaustive. Three fixtures is enough to catch obvious regressions, not edge-case behavior.
- It's not a substitute for unit tests on deterministic code (validator, assembler, money type).

The right mental model: eval is the **smoke test before you ship a prompt or model change**, plus the **safety net while refactoring around the LLM**. Unit tests handle the deterministic core; eval handles the probabilistic edges.

## See also

- [[invoice-to-journal]] — project where this harness pattern is applied
- [[plan-invoice-to-journal]] §10 — the eval table format and fixture conventions
- [[llm-no-arithmetic]] — the eval harness is the right place to catch *judgment* regressions; deterministic balance checks belong in code, not eval
