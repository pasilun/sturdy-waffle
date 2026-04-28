---
title: Interface default methods that return placeholders
type: concept
created: 2026-04-28
updated: 2026-04-28
tags: [java, interfaces, gotcha, observability, audit, code-review]
---

# Interface default methods that return placeholders

**A `default` method that returns `"unknown"`, `"v1"`, `null`, `0`, or any other "safe-looking" placeholder is a silent lie waiting to happen.** Future implementations forget to override it, the placeholder propagates into logs/audit columns/metrics, and you find out months later when a regression hunt turns up a "v1" stamp on a row that should have been "v3".

## The shape of the bug

```java
public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
    default String modelId()       { return "unknown"; }   // ← lies on omission
    default String promptVersion() { return "v1"; }        // ← lies on omission
}

public class AnthropicExtractor implements Extractor {
    // override extract(...)
    // forgot modelId() / promptVersion() — compiler is silent
}
```

Every row written by this implementation now has `model = "unknown"`, `prompt_version = "v1"`. The compiler is happy. The data is poisoned.

## The fix: make the compiler do the work

Drop the defaults. Force every implementation to be explicit:

```java
public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
    String modelId();
    String promptVersion();
}
```

Now any new `Extractor` impl that forgets `modelId()` or `promptVersion()` won't compile. The audit column always reflects reality.

## When defaults *are* fine

`default` methods exist for good reasons — backwards-compatible API evolution, optional capabilities (`default Optional<X> ignoreMe() { return Optional.empty(); }`), genuinely uniform behavior derivable from the rest of the interface. The smell is specifically **defaults that fabricate identity / metadata** that downstream code persists or logs.

Heuristic: **if the value lands in an audit log, a metric label, a database column, or any human-readable diagnostic, it must not have a placeholder default.** Force the override.

## Related smell: silent fallback strings

`catch (Exception e) { return "{}"; }` is the same pattern in error-handling clothing — a safe-looking placeholder that corrupts an audit column on failure. Same fix: let the exception propagate, surface the failure, don't fabricate a value to satisfy the column.

## How it bit Invoice-to-Journal

Phase 3's `Extractor` and `Mapper` ports both shipped with `default modelId() { return "unknown"; }` / `default promptVersion() { return "v1"; }`. The Anthropic implementations did override them, so audit rows were correct *for now* — but the review flagged that any future provider impl could silently forget. Defaults removed; compiler enforces explicitness. Same review caught `JdbcPersister.toJson` returning `"{}"` on `JsonProcessingException`; replaced with `UncheckedIOException` propagation.

## See also

- [[invoice-to-journal]] — where this was caught (Phase 3 review)
- [[extractor-as-provider-seam]] — the interface this rule guards
