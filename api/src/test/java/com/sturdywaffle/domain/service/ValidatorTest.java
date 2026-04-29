package com.sturdywaffle.domain.service;

import com.sturdywaffle.domain.exception.ValidationException;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    private final Validator validator = new Validator();

    @Test
    void acceptsBalancedInvoice() {
        ExtractedInvoice ok = invoice(
                List.of(line("Rent", "8000.00")),
                "8000.00", "2000.00", "10000.00");

        assertDoesNotThrow(() -> validator.validate(ok));
    }

    @Test
    void acceptsZeroVat() {
        ExtractedInvoice ok = invoice(
                List.of(line("Books", "500.00")),
                "500.00", "0.00", "500.00");

        assertDoesNotThrow(() -> validator.validate(ok));
    }

    @Test
    void rejectsLineSumOffByOneCent() {
        ExtractedInvoice bad = invoice(
                List.of(line("A", "100.00"), line("B", "200.00")),
                "300.01", "0.00", "300.01");

        assertThrows(ValidationException.class, () -> validator.validate(bad));
    }

    @Test
    void rejectsNetPlusVatNotEqualGross() {
        ExtractedInvoice bad = invoice(
                List.of(line("Service", "100.00")),
                "100.00", "25.00", "125.01");

        assertThrows(ValidationException.class, () -> validator.validate(bad));
    }

    @Test
    void treatsScaleEquivalentDecimalsAsEqual() {
        // 1.20 vs 1.2 — Money canonicalises to scale 2, so the validator
        // should not reject inputs that differ only in trailing zeros.
        // Pinned because BigDecimal.equals is scale-sensitive (the trap
        // Money was built to dodge — see [[bigdecimal-scale-equality]]).
        ExtractedInvoice ok = invoice(
                List.of(new InvoiceLine("Bagatel", Money.of("1.2"))),
                "1.20", "0.00", "1.20");

        assertDoesNotThrow(() -> validator.validate(ok));
    }

    @Test
    void rejectsEmptyLinesAgainstNonZeroNet() {
        // Reduce on an empty list yields the ZERO seed; if netTotal is
        // non-zero the sum mismatch must surface, not silently pass.
        ExtractedInvoice bad = invoice(
                List.of(),
                "100.00", "25.00", "125.00");

        assertThrows(ValidationException.class, () -> validator.validate(bad));
    }

    private static InvoiceLine line(String desc, String net) {
        return new InvoiceLine(desc, Money.of(net));
    }

    private static ExtractedInvoice invoice(List<InvoiceLine> lines, String net, String vat, String gross) {
        return new ExtractedInvoice(
                "ACME AB", "INV-1", LocalDate.of(2026, 4, 1), "SEK",
                lines, Money.of(net), Money.of(vat), Money.of(gross));
    }
}
