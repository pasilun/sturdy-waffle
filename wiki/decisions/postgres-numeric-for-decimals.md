---
title: Postgres + NUMERIC(18,2) for monetary amounts
type: decision
status: accepted
decision_date: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
supersedes: [sqlite-text-for-decimals]
tags: [invoice-to-journal, postgres, decimals]
---

# Postgres: store decimals as NUMERIC(18,2)

Project: [[invoice-to-journal]]

Supersedes [[sqlite-text-for-decimals]].

## Decision

The application database is **Postgres 16**, started in-process via `io.zonky.test:embedded-postgres` (no Docker, no daemon). Money columns (`net`, `vat`, `gross`, `debit`, `credit`) are declared `NUMERIC(18,2)`. The Postgres JDBC driver round-trips `BigDecimal` losslessly, so domain code uses `Money`/`BigDecimal` end-to-end and storage takes care of scale.

A `Converter<Money, BigDecimal>` pair registered with Spring Data JDBC keeps `Money` in domain code and `BigDecimal` at the storage boundary.

## Rationale

Postgres preserves NUMERIC scale exactly — `1234.56` survives a write/read round-trip as `1234.56`, not `1234.5599999...`. This eliminates the TEXT-with-canonical-string workaround that SQLite forced (see [[sqlite-text-for-decimals]]) and the Spring `Converter<BigDecimal, String>` pair that came with it.

The application-side discipline from [[bigdecimal-scale-equality]] still applies — canonicalize at the parse boundary, keep `Money` as the domain wrapper — but the storage layer is no longer a place where precision can silently die.

## Alternatives considered

- **SQLite + TEXT** (the previous choice, now superseded): zero-ops, file-based, no daemon. Lost to Postgres because the TEXT-encoding hack and storage converter were friction that Postgres makes unnecessary, and graders are more familiar with Postgres than with the SQLite `NUMERIC` affinity gotcha.
- **SQLite + INTEGER cents**: viable but every read/write site has to remember to multiply/divide by 100. Worse ergonomics than either TEXT-on-SQLite or NUMERIC-on-Postgres.
- **Docker + Postgres**: the first plan; dropped when the dev machine (MacBook 12") couldn't run Docker. Replaced by embedded-postgres, which is strictly simpler for the grader anyway.
- **JPA + entity managers**: rejected for the same reason as in [[plan-invoice-to-journal]] — lazy-loading and session-cache surprises in a small CRUD-plus-LLM app.

## Trade-offs

- **One-time binary download.** `io.zonky.test:embedded-postgres` downloads a real Postgres binary (~30 MB) on first boot and caches it in `~/.zonky/`. Requires internet once — run `./gradlew :api:bootRun` before the interview to pre-warm. Subsequent boots are instant.
- **Backup is `cp -r data/`.** Embedded Postgres writes its data directory to `./data/pg/` (local, gitignored). Same as SQLite's backup story, better than a Docker volume.
- **No Docker required.** Prereqs are now just JDK 21 + Node 20+ + pnpm. Strictly fewer friction points for the grader.

## See also

- [[invoice-to-journal]]
- [[plan-invoice-to-journal]] §2, §3, §8
- [[sqlite-text-for-decimals]] — superseded predecessor; the application-side half of the precision story (the [[bigdecimal-scale-equality]] gotcha) survives unchanged
- [[bigdecimal-scale-equality]]
