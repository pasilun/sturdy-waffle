package com.sturdywaffle.domain.service;

import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.model.Posting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Assembler {

    private static final String VAT_ACCOUNT    = "2640";
    private static final String AP_ACCOUNT     = "2440";
    private static final int    SYNTHETIC_LINE = -1;

    public List<Posting> assemble(ExtractedInvoice extracted, List<MappingProposal> proposals) {
        if (proposals.size() != extracted.lines().size()) {
            throw new IllegalArgumentException(
                    "proposals.size() " + proposals.size() + " != lines.size() " + extracted.lines().size());
        }

        List<Posting> postings = new ArrayList<>();

        for (int i = 0; i < extracted.lines().size(); i++) {
            InvoiceLine line = extracted.lines().get(i);
            MappingProposal p = proposals.get(i);
            postings.add(new Posting(
                    i, p.accountCode(), line.net(), null,
                    line.description(), p.reasoning(), p.confidence()));
        }

        // Derive VAT from grossTotal - netTotal so the journal always balances,
        // even when the invoice's printed vatTotal has per-line rounding drift.
        Money effectiveVat = extracted.grossTotal().subtract(extracted.netTotal());
        postings.add(new Posting(
                SYNTHETIC_LINE, VAT_ACCOUNT, effectiveVat, null,
                "Ingående moms", null, null));

        postings.add(new Posting(
                SYNTHETIC_LINE, AP_ACCOUNT, null, extracted.grossTotal(),
                "Leverantörsskuld " + extracted.supplierName(), null, null));

        assertBalanced(postings);
        return postings;
    }

    private void assertBalanced(List<Posting> postings) {
        BigDecimal debits = postings.stream()
                .filter(p -> p.debit() != null)
                .map(p -> p.debit().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credits = postings.stream()
                .filter(p -> p.credit() != null)
                .map(p -> p.credit().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException(
                    "Journal not balanced: debits=" + debits + " credits=" + credits);
        }
    }
}
