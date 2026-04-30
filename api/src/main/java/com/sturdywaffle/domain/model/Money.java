package com.sturdywaffle.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value) {

    public Money {
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money of(String s) {
        return new Money(new BigDecimal(s));
    }

    public static Money of(BigDecimal bd) {
        return new Money(bd);
    }

    public Money add(Money other) {
        return new Money(value.add(other.value));
    }

    public Money subtract(Money other) {
        return new Money(value.subtract(other.value));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
