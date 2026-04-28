package com.sturdywaffle.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value) {

    public static Money of(String s) {
        return new Money(new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY));
    }

    public static Money of(BigDecimal bd) {
        return new Money(bd.setScale(2, RoundingMode.UNNECESSARY));
    }

    public Money add(Money other) {
        return new Money(value.add(other.value));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
