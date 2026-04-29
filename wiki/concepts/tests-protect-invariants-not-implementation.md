---
title: Tests protect invariants, not implementation
description: Smart tests assert what the spec promises, not how the code happens to do it. The four-question filter for deciding whether a test will earn rent or rot.
type: concept
created: 2026-04-29
updated: 2026-04-29
tags: [testing, methodology, refactoring]
---

# Tests protect invariants, not implementation

A test earns rent forever if it goes green through any change that preserves the spec's promises, and goes red the moment one of those promises breaks. A test that goes red on cosmetic refactors is debt — it slowly trains the team to delete failing tests instead of fixing them.

## The four-question filter

Before writing a test, ask:

1. **If I rename a class or method, does this test go red?** If yes, it's testing implementation. Skip it. (Mockito `verify(persister).recordDecision(...)` style.)
2. **If I refactor SQL but the result is identical, does it go red?** If yes, you're asserting the SQL string. Bad. Assert the *result* — round-trip the data through the persister and read it back.
3. **If a teammate adds a new field to a DTO, does it go red?** If yes (snapshot tests, exact-shape assertions), kill it. Assert specific fields, not whole-object equality.
4. **If the LLM picks 6540 instead of 6550, does my unit test go red?** If yes, you're testing model output, not deterministic logic. That's the eval harness's job — separate concern, separate cadence, separate noise budget.

A test that survives all four is a test that protects an invariant.

## What goes at each layer

| Layer | What it tests | What it must NOT do |
|---|---|---|
| **Domain unit** | Pure-function invariants from the spec. `net + vat == gross`. `sum(lines) == net`. Balanced postings. `debit XOR credit`. `Money` scale. | Reach into Spring. Touch the DB. Mock anything. |
| **Application** | Service-layer composition with real domain objects + mocked ports (LLM, persistence). One test per public method, asserting *what* changed, not which dependency was called. | `verify(extractor)`-style mock assertions. Mocking pure domain services (Validator, Assembler) — they're free, run them. |
| **Persistence integration** | SQL round-trips against the same DB engine prod uses (embedded Postgres, not H2). Lock/conflict semantics. NULL-typing edge cases. Audit-row invariants. | Mock JDBC. Use a different DB engine (H2 silently allowed `WHERE (? IS NULL OR …)` that Postgres rejected — Phase 5 bug). |
| **Contract / e2e** | Read-only smokes against the real backend, plus mocked-API UI specs for fast feedback. | Burn LLM credits in the default tier. Assert exact JSON payloads (couples to schema). |
| **Eval (separate beast)** | Model/prompt regression via expected mappings on stable PDFs. Tolerant of non-determinism — N≥3 runs or `temperature: 0`. | Run on every commit (slow, costs $$). Be treated as a unit test (it's a regression canary). |

## Anti-patterns that show up most

- **Mock-heavy tests at the SQL boundary.** A mocked `JdbcTemplate` can't reproduce Postgres-specific behaviour like prepared-statement parameter type inference, `NUMERIC` scale arithmetic, or constraint violations. The Phase 5 `WHERE (? IS NULL OR …)` bug returned 500 against real Postgres but would have passed any mocked test. See [[postgres-numeric-for-decimals]].
- **Snapshot / exact-equality tests on API responses.** Adding a non-breaking field (e.g. a new `mapping.escalated` event type, an optional confidence value) goes red. The fix is always either updating the snapshot blindly (which defeats the test) or deleting it.
- **Asserting framework guarantees.** Spring autowiring works. Jackson serialises records. Don't write a test for that — write one for *your* logic.
- **Asserting LLM output deterministically in unit tests.** "Test that AnthropicMapper returns 6540 for 'webbutveckling'" is brittle and burns credits. Mocking the LLM defeats the point. Send it to the eval harness; see [[llm-eval-as-dev-canary]].
- **Coupling tests to file paths or method names** (`verify(...).method()` chains). These break on every refactor and add no signal.

## What to do instead

- **Round-trip tests over assertion-on-call tests.** If you wrote it, read it back; the assertion is on the resulting state, not on the act of writing.
- **Boundary tests over happy-path floods.** One happy-path test plus the boundary cases (off-by-one cents, NULL filter, decided-row lock) does more work than ten happy-path tests.
- **Real DB for SQL code, real domain for service code.** Embedded Postgres for persistence integration; let `Validator`/`Assembler` run for free in application tests.
- **Mock at seams designed for it.** `Extractor` and `Mapper` ports exist precisely so the LLM is mockable in service tests without leaking provider details — see [[extractor-as-provider-seam]].
- **Pre-commit gates the cheap tier; pre-push gates the integration tier.** Husky already structures this — keep the pyramid asymmetric so devs feel fast, CI feels thorough.

## Example: testing the escalate-mapping feature

Wrong shape (implementation):
```java
verify(persister).replaceMapping(eq(suggestionId), anyList(), any(ModelRun.class));
verify(jdbc).update(contains("DELETE FROM postings"), any());
```

Right shape (invariant):
```java
// Given a pending suggestion with mappings from the primary model
// When escalate is called
// Then postings are replaced, suggestions.model is updated, and an
//      audit row "mapping.escalated" exists with from/to in the payload
//
// Calling escalate on a decided suggestion throws ConflictException.
// (Tests use real embedded Postgres, real Validator + Assembler, mocked
//  Extractor + Mapper since those are the LLM seams.)
```

The right shape survives any refactor of `JdbcPersister.replaceMapping` — switching from `DELETE`+`INSERT` to `INSERT … ON CONFLICT`, splitting the method, or moving it behind a different facade — as long as the externally observable invariants hold.

## When to break the rule

Sometimes you genuinely *do* want to pin an implementation detail because past incidents showed it costs you when it changes silently. Examples in this codebase:

- The `setCleanDataDirectory(false)` line in `PostgresConfig` — a test that asserts the embedded cluster persists across boots is technically implementation-coupled, but it pays for itself the next time someone "cleans up" that line. See [[embedded-postgres-clean-data-gotcha]].
- The Vite proxy `bypass` callback — pinned by the live e2e tier because the bug it prevents is invisible from any other vantage.

The rule isn't "never test implementation." It's: **if you're testing implementation, you should be able to point to the past incident that justifies the cost.** Most of the time, you can't — and those tests are the ones to skip.
