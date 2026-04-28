package com.sturdywaffle.domain.service;

import com.sturdywaffle.domain.exception.ValidationException;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.Money;

import java.math.BigDecimal;

public class Validator {

    public void validate(ExtractedInvoice e) {
        BigDecimal lineSum = e.lines().stream()
                .map(l -> l.net().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (lineSum.compareTo(e.netTotal().value()) != 0) {
            throw new ValidationException(
                    "Line net sum " + lineSum + " != netTotal " + e.netTotal().value());
        }

        BigDecimal expectedGross = e.netTotal().value().add(e.vatTotal().value());
        if (expectedGross.compareTo(e.grossTotal().value()) != 0) {
            throw new ValidationException(
                    "netTotal + vatTotal " + expectedGross + " != grossTotal " + e.grossTotal().value());
        }
    }
}
