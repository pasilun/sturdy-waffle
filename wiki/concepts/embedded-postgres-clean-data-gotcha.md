---
title: embedded-postgres setCleanDataDirectory gotcha
type: concept
created: 2026-04-28
updated: 2026-04-28
tags: [postgres, embedded, java, gotcha, persistence]
---

# embedded-postgres `setCleanDataDirectory` gotcha

`io.zonky.test:embedded-postgres` (v2.x) **reinitializes the Postgres cluster on every `start()` call by default**, even when the data directory already contains a valid cluster. This wipes all data silently.

## The symptom

Flyway shows `Schema history table does not exist yet` and `Current version: << Empty Schema >>` on every boot — as if the database was freshly created each time.

## The fix

```java
EmbeddedPostgres.builder()
    .setPort(5432)
    .setDataDirectory(dataDir)
    .setCleanDataDirectory(false)  // ← required for state to persist
    .start();
```

`setCleanDataDirectory(false)` tells the library to skip `initdb` when a valid cluster already exists in the data directory. First boot still runs `initdb` (the directory is empty); subsequent boots just start the existing cluster.

## Why the default is `true`

The library was designed for testing. Test runs want a fresh database every time; persistence is the exception, not the rule. The API doesn't make this default visible until you check the builder Javadoc or notice that your data vanishes.

## Verification

With `setCleanDataDirectory(false)`:
- First boot: `initdb` output + Flyway "applying V1"
- Subsequent boots: no `initdb` output + Flyway "Current version: 1 — no migration necessary"

## See also

- [[invoice-to-journal]] — where this was discovered
- [[postgres-numeric-for-decimals]] — the broader Postgres storage decision
