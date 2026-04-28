package com.sturdywaffle.domain.model;

public record Posting(
        int lineIndex,
        String accountCode,
        Money debit,
        Money credit,
        String description,
        String reasoning,
        Double confidence
) {}
