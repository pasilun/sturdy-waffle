package com.sturdywaffle.web.dto;

public record PostingResponse(
        int lineIndex,
        String accountCode,
        String accountName,
        String debit,
        String credit,
        String description,
        String reasoning,
        Double confidence
) {}
