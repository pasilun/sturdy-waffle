package com.sturdywaffle.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void canonicalisesScaleToTwo() {
        // The whole point of Money is to dodge BigDecimal.equals being
        // scale-sensitive. After Money.of, every value has scale 2.
        // See [[bigdecimal-scale-equality]] and
        // [[tests-protect-invariants-not-implementation]].
        assertEquals(2, Money.of("1.2").value().scale());
        assertEquals(2, Money.of("1.20").value().scale());
        assertEquals(2, Money.of(BigDecimal.ZERO).value().scale());
    }

    @Test
    void equalsTreatsTrailingZerosAsSameValue() {
        assertEquals(Money.of("1.2"), Money.of("1.20"));
        assertEquals(Money.of("100"), Money.of("100.00"));
    }

    @Test
    void rawBigDecimalEqualsIsScaleSensitive() {
        // This is the trap Money exists to plaster over: kept as a test
        // because if anyone "simplifies" Money to a plain BigDecimal
        // wrapper, the rest of the codebase silently breaks at the
        // first 1.20 vs 1.2 comparison.
        assertNotEquals(new BigDecimal("1.2"), new BigDecimal("1.20"));
    }

    @Test
    void addPreservesScale() {
        Money sum = Money.of("1.20").add(Money.of("2.30"));
        assertEquals(Money.of("3.50"), sum);
        assertEquals(2, sum.value().scale());
    }

    @Test
    void rejectsMoreThanTwoDecimalsViaUnnecessaryRounding() {
        // RoundingMode.UNNECESSARY throws if rescale would lose data —
        // that's the guard against accidentally dropping cents on input.
        assertThrows(ArithmeticException.class, () -> Money.of("1.234"));
    }

    @Test
    void toStringReturnsPlainDecimal() {
        // The DB stores NUMERIC(18,2); the wire format is plain decimal
        // strings. toPlainString avoids scientific notation creeping in
        // for very large or very small magnitudes.
        assertEquals("1.20", Money.of("1.2").toString());
        assertEquals("0.00", Money.of(BigDecimal.ZERO).toString());
    }
}
