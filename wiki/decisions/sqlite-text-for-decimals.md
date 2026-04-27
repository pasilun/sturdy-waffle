---
title: Store decimals as TEXT in SQLite, not NUMERIC
type: decision
status: superseded
decision_date: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
superseded_by: [postgres-numeric-for-decimals]
tags: [invoice-to-journal, sqlite, decimals]
---

# SQLite: store decimals as TEXT, not NUMERIC

Project: [[invoice-to-journal]]

> **Superseded 2026-04-27** by [[postgres-numeric-for-decimals]]. The DB choice changed from SQLite to Postgres later the same day, which makes the "SQLite NUMERIC affinity coerces to REAL" problem moot — Postgres `NUMERIC(18,2)` preserves scale exactly, so the TEXT-encoding workaround is no longer needed. The application-side half of the precision story (the [[bigdecimal-scale-equality]] gotcha) is unchanged. This page is kept for the rationale: if SQLite ever comes back into the conversation, the reasoning here still applies.

## Decision

Money columns (`net`, `vat`, `gross`, `debit`, `credit`) are declared `TEXT` and hold canonical decimal strings like `"1234.56"`. They are not `NUMERIC` or `REAL`.

A small Spring `Converter<BigDecimal, String>` pair handles the round-trip and enforces scale 2 on write.

## Rationale

SQLite's NUMERIC affinity coerces non-integer values to REAL (IEEE 754 double), which loses decimal precision. `1234.56` round-trips to `1234.5599999999...` and the careful scale-2 invariant from [[bigdecimal-scale-equality]] is silently destroyed at storage.

TEXT preserves the canonical form exactly. The conversion layer is the single place where the rule "scale must be 2 on write" lives, so the rest of the code works in pure `BigDecimal` (or `Money`) space.

## Alternatives considered

- **NUMERIC**: rejected — affinity coerces to REAL.
- **REAL**: rejected — same precision loss, more honest about it.
- **INTEGER (cents)**: viable, but forces every read site to remember to divide by 100 and every write to multiply. The TEXT approach keeps `BigDecimal` semantics end-to-end with one small conversion layer. If a future invoice required four decimals (forex), TEXT handles it; INTEGER would need a schema migration.

## Caveats

- Sorting by amount in SQL won't be numeric without a `CAST(net AS REAL)`. Acceptable: there's no v1 query path that sorts by amount.
- Sums in SQL aren't possible on TEXT columns. Acceptable: aggregation happens in Java, where `BigDecimal.add` is exact.

## See also

- [[invoice-to-journal]]
- [[plan-invoice-to-journal]] §3, §7
- [[bigdecimal-scale-equality]] — the application-side half of the same problem
