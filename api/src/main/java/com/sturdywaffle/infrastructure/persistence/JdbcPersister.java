package com.sturdywaffle.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sturdywaffle.application.Persister;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!eval")
public class JdbcPersister implements Persister {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcPersister(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SuggestionId persist(byte[] pdf, ExtractedInvoice extracted, List<Posting> postings,
                                String extractorModel, String extractorPromptVersion, long extractionLatencyMs,
                                String mapperModel, String mapperPromptVersion, long mappingLatencyMs) {
        UUID invoiceId = UUID.randomUUID();
        String pdfPath = storePdf(pdf, invoiceId);

        jdbc.update("""
                INSERT INTO invoices (id, supplier_name, invoice_number, invoice_date,
                    currency, net, vat, gross, pdf_path)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                invoiceId,
                extracted.supplierName(),
                extracted.invoiceNumber(),
                extracted.invoiceDate(),
                extracted.currency(),
                extracted.netTotal().value(),
                extracted.vatTotal().value(),
                extracted.grossTotal().value(),
                pdfPath);

        UUID extractionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO extractions (id, invoice_id, raw_json, model, prompt_version, latency_ms)
                VALUES (?,?,?,?,?,?)
                """,
                extractionId, invoiceId, toJson(extracted),
                extractorModel, extractorPromptVersion, extractionLatencyMs);

        UUID suggestionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO suggestions (id, invoice_id, extraction_id, model, prompt_version, latency_ms)
                VALUES (?,?,?,?,?,?)
                """,
                suggestionId, invoiceId, extractionId,
                mapperModel, mapperPromptVersion, mappingLatencyMs);

        for (Posting p : postings) {
            jdbc.update("""
                    INSERT INTO postings (suggestion_id, line_index, account_code,
                        debit, credit, description, reasoning, confidence)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    suggestionId,
                    p.lineIndex(),
                    p.accountCode(),
                    p.debit() != null ? p.debit().value() : null,
                    p.credit() != null ? p.credit().value() : null,
                    p.description(),
                    p.reasoning(),
                    p.confidence());
        }

        return new SuggestionId(suggestionId);
    }

    @Override
    @Transactional
    public void recordDecision(UUID suggestionId, String status, String note) {
        jdbc.update("""
                INSERT INTO decisions (suggestion_id, status, note)
                VALUES (?,?,?)
                ON CONFLICT (suggestion_id) DO UPDATE
                    SET status = EXCLUDED.status, note = EXCLUDED.note, decided_at = NOW()
                """,
                suggestionId, status, note);
    }

    private String storePdf(byte[] pdf, UUID invoiceId) {
        Path dir = Path.of("data/uploads");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(invoiceId + ".pdf");
            Files.write(file, pdf);
            return file.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toJson(ExtractedInvoice extracted) {
        try {
            return objectMapper.writeValueAsString(extracted);
        } catch (Exception e) {
            return "{}";
        }
    }
}
