package com.sturdywaffle.domain.model;

import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoice(
        String supplierName,
        String invoiceNumber,
        LocalDate invoiceDate,
        String currency,
        List<InvoiceLine> lines,
        Money netTotal,
        Money vatTotal,
        Money grossTotal
) {}
