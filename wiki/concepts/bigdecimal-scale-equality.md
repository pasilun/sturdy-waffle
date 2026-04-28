---
title: BigDecimal scale-equality gotcha
type: concept
created: 2026-04-27
updated: 2026-04-27
tags: [java, bigdecimal, money, gotcha, decimal, equality]
---

# BigDecimal scale-equality gotcha

**`BigDecimal.equals` is scale-sensitive. `new BigDecimal("1.20").equals(new BigDecimal("1.2"))` is `false`.**

This is one of the most common sources of silent money bugs in Java. Two values that are mathematically equal fail an equality check because they have different scales (number of decimal places stored internally).

## The problem

```java
new BigDecimal("1.20").equals(new BigDecimal("1.2"))   // false — different scale
new BigDecimal("1.20").compareTo(new BigDecimal("1.2")) // 0 — mathematically equal
```

The correct comparison for "is this the same amount?" is `compareTo(other) == 0`, not `.equals(other)`. But scattering `compareTo` throughout business code is error-prone and easy to forget.

## The fix: canonicalize once at the boundary

Parse every amount once, on ingress, to a canonical scale. If you always canonicalize to scale 2, then `.equals` is safe everywhere because all instances share the same scale.

```java
new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY)
// UNNECESSARY throws ArithmeticException if the input has more than 2 decimal places.
// That's a feature: an extraction that returns "1.234" is a bug to surface, not round away.
```

## The `Money` value type

In the Invoice-to-Journal project ([[invoice-to-journal]]), all monetary amounts are wrapped in a `Money` record:

```java
public record Money(BigDecimal value) {
    public static Money of(String s) {
        return new Money(new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY));
    }
    public Money add(Money other) { return new Money(value.add(other.value)); }
    // .equals() is safe because every instance is canonicalized to scale 2
}
```

This keeps `BigDecimal` out of business logic and the scale gotcha out of reach. All money enters via `Money.of()`, never via `new BigDecimal(...)` directly.

## Storage round-trip

Postgres `NUMERIC(18,2)` preserves scale exactly — a `BigDecimal` at scale 2 round-trips losslessly via the JDBC driver. No string encoding needed. See [[postgres-numeric-for-decimals]].

## See also

- [[postgres-numeric-for-decimals]] — storage decision that this gotcha informs
- [[sqlite-text-for-decimals]] — superseded approach (TEXT encoding was a workaround for SQLite's lack of a fixed-scale decimal type)
- [[plan-invoice-to-journal]] §7 — numeric handling section in the plan
