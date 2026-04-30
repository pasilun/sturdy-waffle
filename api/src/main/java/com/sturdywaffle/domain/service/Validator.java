package com.sturdywaffle.domain.service;

import com.sturdywaffle.domain.exception.ValidationException;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.Money;

import java.math.BigDecimal;

public class Validator {

    private static final Money ZERO = Money.of(BigDecimal.ZERO);
    // Real invoices round VAT per line; tolerate up to 0.10 in the gross check.
    private static final BigDecimal GROSS_TOLERANCE = new BigDecimal("0.10");

    public void validate(ExtractedInvoice e) {
        Money lineSum = e.lines().stream()
                .map(l -> l.net())
                .reduce(ZERO, Money::add);

        if (!lineSum.equals(e.netTotal())) {
            throw new ValidationException(
                    "Line net sum " + lineSum + " != netTotal " + e.netTotal());
        }

        Money expectedGross = e.netTotal().add(e.vatTotal());
        BigDecimal diff = expectedGross.value().subtract(e.grossTotal().value()).abs();
        if (diff.compareTo(GROSS_TOLERANCE) > 0) {
            throw new ValidationException(
                    "netTotal + vatTotal " + expectedGross + " != grossTotal " + e.grossTotal());
        }
    }
}
