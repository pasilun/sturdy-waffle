package com.sturdywaffle.web.dto;

import java.util.List;
import java.util.UUID;

public record SuggestionResponse(
        UUID id,
        UUID invoiceId,
        String supplierName,
        String invoiceNumber,
        String invoiceDate,
        String currency,
        String net,
        String vat,
        String gross,
        List<PostingResponse> postings,
        DecisionResponse decision
) {}
