package com.sturdywaffle.domain.service;

import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.model.Posting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssemblerTest {

    private final Assembler assembler = new Assembler();

    @Test
    void emitsLineCountPlusVatPlusApPostings() {
        ExtractedInvoice inv = invoice(List.of(
                line("AWS", "1000.00"),
                line("Slack", "500.00")),
                "1500.00", "375.00", "1875.00");

        List<Posting> postings = assembler.assemble(inv, List.of(
                proposal("6550"), proposal("6560")));

        assertEquals(4, postings.size(), "2 line postings + 1 VAT + 1 AP");
    }

    @Test
    void linePostingsAreDebitsCarryingProposalAccount() {
        ExtractedInvoice inv = invoice(List.of(line("Rent", "8000.00")),
                "8000.00", "2000.00", "10000.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("5010")));
        Posting linePosting = postings.get(0);

        assertEquals(0, linePosting.lineIndex());
        assertEquals("5010", linePosting.accountCode());
        assertEquals(Money.of("8000.00"), linePosting.debit());
        assertNull(linePosting.credit());
    }

    @Test
    void apRowCreditsLeverantorsskuldWithGross() {
        ExtractedInvoice inv = invoice(List.of(line("Rent", "8000.00")),
                "8000.00", "2000.00", "10000.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("5010")));
        Posting ap = postings.stream()
                .filter(p -> "2440".equals(p.accountCode()))
                .findFirst().orElseThrow();

        assertNull(ap.debit());
        assertEquals(Money.of("10000.00"), ap.credit());
    }

    @Test
    void vatRowDebitsIngaendeMomsWithVatTotal() {
        ExtractedInvoice inv = invoice(List.of(line("Rent", "8000.00")),
                "8000.00", "2000.00", "10000.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("5010")));
        Posting vat = postings.stream()
                .filter(p -> "2640".equals(p.accountCode()))
                .findFirst().orElseThrow();

        assertEquals(Money.of("2000.00"), vat.debit());
        assertNull(vat.credit());
    }

    @Test
    void everyPostingIsDebitXorCredit() {
        // Mirrors the DB CHECK constraint: never both, never neither.
        ExtractedInvoice inv = invoice(List.of(
                line("A", "100.00"), line("B", "200.00")),
                "300.00", "75.00", "375.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("6540"), proposal("6540")));

        for (Posting p : postings) {
            boolean hasDebit = p.debit() != null;
            boolean hasCredit = p.credit() != null;
            assertTrue(hasDebit ^ hasCredit,
                    "posting must be debit XOR credit, got " + p);
        }
    }

    @Test
    void debitsEqualCredits() {
        // Assembler asserts internally; this test pins the spec invariant.
        ExtractedInvoice inv = invoice(List.of(
                line("A", "123.45"), line("B", "678.90")),
                "802.35", "200.59", "1002.94");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("6540"), proposal("6540")));

        BigDecimal debits = postings.stream()
                .filter(p -> p.debit() != null)
                .map(p -> p.debit().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = postings.stream()
                .filter(p -> p.credit() != null)
                .map(p -> p.credit().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, debits.compareTo(credits),
                "debits=" + debits + " credits=" + credits);
    }

    @Test
    void zeroVatProducesZeroVatPostingNotMissingOne() {
        // Even when VAT is zero we still emit a 2640 posting (with debit
        // zero) — the Assembler keeps the row count predictable so
        // downstream code doesn't branch on VAT presence.
        ExtractedInvoice inv = invoice(List.of(line("Books", "500.00")),
                "500.00", "0.00", "500.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("6420")));

        assertEquals(3, postings.size());
        Posting vat = postings.stream()
                .filter(p -> "2640".equals(p.accountCode()))
                .findFirst().orElseThrow();
        assertEquals(Money.of("0.00"), vat.debit());
    }

    @Test
    void linePostingPreservesProposalReasoningAndConfidence() {
        ExtractedInvoice inv = invoice(List.of(line("DevOps", "1000.00")),
                "1000.00", "250.00", "1250.00");

        List<Posting> postings = assembler.assemble(inv, List.of(
                new MappingProposal("6540", "Consultancy work", 0.92)));

        Posting linePosting = postings.get(0);
        assertEquals("Consultancy work", linePosting.reasoning());
        assertEquals(0.92, linePosting.confidence());
        assertEquals("DevOps", linePosting.description());
    }

    @Test
    void syntheticPostingsHaveNullConfidence() {
        // 2640 and 2440 are deterministic; null confidence is the signal
        // that the LLM didn't pick them. Phase 6 review caught a UI bug
        // where these rendered "NaN%" — the data shape is the contract.
        ExtractedInvoice inv = invoice(List.of(line("Rent", "8000.00")),
                "8000.00", "2000.00", "10000.00");

        List<Posting> postings = assembler.assemble(inv, List.of(proposal("5010")));

        Posting vat = postings.stream().filter(p -> "2640".equals(p.accountCode())).findFirst().orElseThrow();
        Posting ap  = postings.stream().filter(p -> "2440".equals(p.accountCode())).findFirst().orElseThrow();

        assertNull(vat.confidence());
        assertNull(ap.confidence());
        assertNotNull(postings.get(0).confidence(), "line posting still carries the proposal's confidence");
    }

    @Test
    void rejectsProposalCountMismatch() {
        ExtractedInvoice inv = invoice(List.of(
                line("A", "100.00"), line("B", "200.00")),
                "300.00", "0.00", "300.00");

        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(inv, List.of(proposal("6540"))));
    }

    private static InvoiceLine line(String desc, String net) {
        return new InvoiceLine(desc, Money.of(net));
    }

    private static MappingProposal proposal(String code) {
        return new MappingProposal(code, "test", 0.9);
    }

    private static ExtractedInvoice invoice(List<InvoiceLine> lines, String net, String vat, String gross) {
        return new ExtractedInvoice(
                "ACME AB", "INV-1", LocalDate.of(2026, 4, 1), "SEK",
                lines, Money.of(net), Money.of(vat), Money.of(gross));
    }
}
