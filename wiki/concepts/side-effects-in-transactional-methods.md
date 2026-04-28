---
title: Side effects inside @Transactional methods
type: concept
created: 2026-04-28
updated: 2026-04-28
tags: [java, spring, transactions, gotcha, persistence, dual-write]
---

# Side effects inside `@Transactional` methods

**Anything that isn't a DB call should usually run outside the transaction.** Filesystem writes, HTTP calls, message-queue publishes, and Anthropic/OpenAI requests inside an `@Transactional` method silently widen the transaction and create dual-write inconsistencies.

## The two problems

1. **The transaction lives longer than it needs to.** A DB connection is checked out for the whole `@Transactional` body. File IO and network calls add seconds; the connection pool sees pressure that isn't a DB problem.
2. **Roll-back can't unwind the side effect.** If the SQL fails after `Files.write(...)`, the file stays on disk as an orphan. If the SQL commits but the post-commit network call fails, the DB and the external system disagree forever. This is the textbook *dual-write problem*.

## The pattern

Split the public method along the side-effect boundary. Spring AOP only proxies methods called *through the bean* (not self-invocation), so two public methods on the same bean can have independent transaction semantics:

```java
public interface Persister {
    record StoredPdf(UUID invoiceId, String pdfPath) {}

    StoredPdf storePdf(byte[] pdf);                 // no @Transactional — file IO

    @Transactional
    SuggestionId persist(StoredPdf stored, ...);   // DB only
}
```

The caller (here `PipelineService`) sequences the two:

```java
StoredPdf stored = persister.storePdf(pdf);   // file written, no DB conn held
return persister.persist(stored, ...);        // tx opens, all SQL, tx commits
```

A failure in `persist` after `storePdf` succeeded leaves an orphan file. That's still better than the alternative (the file was inside the tx so a roll-back leaves the file *and* lies about it), and an orphan-PDF reaper is a much simpler thing to reason about than a DB+filesystem reconciliation job.

## When it's OK to keep the side effect inside

- The side effect is itself transactional and joined to the same transaction (e.g. another DB write via the same `JdbcTemplate`).
- The side effect is idempotent and the orphaned-write case is acceptable (audit-log inserts to the same DB are fine).
- The side effect is cheap and can't fail (bumping an in-memory counter).

## How it bit Invoice-to-Journal

Phase 3's first cut had `Files.write(pdf, path)` inside `JdbcPersister.persist`'s `@Transactional` body. The review (2026-04-28) flagged it; fix was to add `Persister.storePdf` as a separate non-transactional method on the same interface. See [[invoice-to-journal]] Phase 3 status.

## See also

- [[invoice-to-journal]] — where this lesson was learned
- [[plan-invoice-to-journal]] §3 — the pipeline's step boundaries
