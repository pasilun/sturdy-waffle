package com.sturdywaffle.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceListItem(
        UUID suggestionId,
        UUID invoiceId,
        String supplierName,
        String invoiceNumber,
        LocalDate invoiceDate,
        String currency,
        String gross,
        String status,
        Instant decidedAt,
        Instant createdAt) {
}
