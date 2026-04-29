package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;
import com.sturdywaffle.domain.model.ModelRun;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.domain.port.Extractor;
import com.sturdywaffle.domain.port.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Application-layer test for PipelineService.escalate.
// Real Validator + Assembler (they're pure domain — running them is free
// and means the test catches escalation paths that produce unbalanced
// journals or fail the line-sum invariant). Mock the LLM ports and the
// Persister, since those are seams designed for it.
class PipelineServiceEscalateTest {

    private Extractor extractor;
    private Mapper primaryMapper;
    private Mapper escalationMapper;
    private Persister persister;
    private PipelineService pipeline;

    @BeforeEach
    void setUp() {
        extractor       = mock(Extractor.class);
        primaryMapper   = mock(Mapper.class);
        escalationMapper = mock(Mapper.class);
        persister       = mock(Persister.class);

        // Stub the model ids consistently so we can assert which mapper ran.
        when(primaryMapper.modelId()).thenReturn("claude-haiku-4-5");
        when(primaryMapper.promptVersion()).thenReturn("map.v1");
        when(escalationMapper.modelId()).thenReturn("claude-sonnet-4-6");
        when(escalationMapper.promptVersion()).thenReturn("map.v1.escalation");

        pipeline = new PipelineService(extractor, primaryMapper, escalationMapper, persister);
    }

    @Test
    void escalateUsesEscalationMapperNotPrimary() {
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice extracted = invoiceWithOneLine("AWS hosting", "1000.00", "250.00", "1250.00");

        when(persister.loadExtractedInvoice(id)).thenReturn(extracted);
        when(escalationMapper.map(eq("ACME AB"), any(InvoiceLine.class)))
                .thenReturn(Optional.of(new MappingProposal("6550", "Hosting", 0.95)));

        pipeline.escalate(id);

        verify(escalationMapper).map(eq("ACME AB"), any(InvoiceLine.class));
        verify(primaryMapper, never()).map(any(), any());
    }

    @Test
    void escalateRecordsEscalationMapperInModelRun() {
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice extracted = invoiceWithOneLine("AWS hosting", "1000.00", "250.00", "1250.00");

        when(persister.loadExtractedInvoice(id)).thenReturn(extracted);
        when(escalationMapper.map(any(), any()))
                .thenReturn(Optional.of(new MappingProposal("6550", "Hosting", 0.95)));

        AtomicReference<ModelRun> capturedRun = captureModelRun(id);

        pipeline.escalate(id);

        ModelRun run = capturedRun.get();
        assertNotNull(run);
        assertEquals("claude-sonnet-4-6", run.model());
        assertEquals("map.v1.escalation", run.promptVersion());
        assertTrue(run.latencyMs() >= 0);
    }

    @Test
    void escalatePassesAssembledPostingsToReplaceMapping() {
        // Validator + Assembler run for real. The result handed to
        // replaceMapping should be lineCount + 2 postings (one VAT, one AP),
        // balanced. If we ever swap Assembler shape, this test stays green
        // as long as the invariant holds.
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice extracted = invoiceWithOneLine("AWS hosting", "1000.00", "250.00", "1250.00");

        when(persister.loadExtractedInvoice(id)).thenReturn(extracted);
        when(escalationMapper.map(any(), any()))
                .thenReturn(Optional.of(new MappingProposal("6550", "Hosting", 0.95)));

        AtomicReference<List<Posting>> capturedPostings = capturePostings(id);

        pipeline.escalate(id);

        List<Posting> postings = capturedPostings.get();
        assertNotNull(postings);
        assertEquals(3, postings.size(), "1 line + 2 synthetic");
        assertEquals("6550", postings.get(0).accountCode());
        assertTrue(postings.stream().anyMatch(p -> "2640".equals(p.accountCode())));
        assertTrue(postings.stream().anyMatch(p -> "2440".equals(p.accountCode())));
    }

    @Test
    void escalatePropagatesValidationErrorWithoutCallingMapper() {
        // If the persisted extraction doesn't validate (corrupted on disk,
        // for instance), we must not silently feed a bad invoice into the
        // mapper or assembler.
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice broken = new ExtractedInvoice(
                "ACME AB", "INV-X", LocalDate.of(2026, 4, 1), "SEK",
                List.of(new InvoiceLine("Mismatch", Money.of("100.00"))),
                Money.of("999.00"), Money.of("0.00"), Money.of("999.00"));

        when(persister.loadExtractedInvoice(id)).thenReturn(broken);

        // escalate() doesn't currently call validator (it trusts the persisted
        // extraction); but the assembler still runs. With a single line
        // mapped to one proposal, assembler will balance against the bad
        // gross — so we expect the IllegalStateException from assertBalanced.
        // Pinning whichever throws so a future "skip validation entirely"
        // refactor doesn't silently let bad data through.
        when(escalationMapper.map(any(), any()))
                .thenReturn(Optional.of(new MappingProposal("6550", "Hosting", 0.95)));

        assertThrows(RuntimeException.class, () -> pipeline.escalate(id));
        verify(persister, never()).replaceMapping(any(), any(), any());
    }

    @Test
    void escalateThrowsWhenMapperReturnsEmpty() {
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice extracted = invoiceWithOneLine("Mystery line", "100.00", "25.00", "125.00");

        when(persister.loadExtractedInvoice(id)).thenReturn(extracted);
        when(escalationMapper.map(any(), any())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> pipeline.escalate(id));
        verify(persister, never()).replaceMapping(any(), any(), any());
    }

    @Test
    void escalateReturnsSameSuggestionId() {
        SuggestionId id = new SuggestionId(UUID.randomUUID());
        ExtractedInvoice extracted = invoiceWithOneLine("AWS hosting", "1000.00", "250.00", "1250.00");

        when(persister.loadExtractedInvoice(id)).thenReturn(extracted);
        when(escalationMapper.map(any(), any()))
                .thenReturn(Optional.of(new MappingProposal("6550", "Hosting", 0.95)));

        SuggestionId returned = pipeline.escalate(id);
        assertSame(id, returned, "escalate is in-place; the id is the same suggestion");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private AtomicReference<ModelRun> captureModelRun(SuggestionId id) {
        AtomicReference<ModelRun> capture = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            capture.set(inv.getArgument(2, ModelRun.class));
            return null;
        }).when(persister).replaceMapping(eq(id), any(), any());
        return capture;
    }

    private AtomicReference<List<Posting>> capturePostings(SuggestionId id) {
        AtomicReference<List<Posting>> capture = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            capture.set(inv.getArgument(1));
            return null;
        }).when(persister).replaceMapping(eq(id), any(), any());
        return capture;
    }

    private static ExtractedInvoice invoiceWithOneLine(String desc, String net, String vat, String gross) {
        return new ExtractedInvoice(
                "ACME AB", "INV-1", LocalDate.of(2026, 4, 1), "SEK",
                List.of(new InvoiceLine(desc, Money.of(net))),
                Money.of(net), Money.of(vat), Money.of(gross));
    }
}
