package com.sturdywaffle.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sturdywaffle.application.Persister;
import com.sturdywaffle.domain.exception.NotFoundException;
import com.sturdywaffle.domain.model.DecisionStatus;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.ModelRun;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.web.dto.DecisionResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    public StoredPdf storePdf(byte[] pdf) {
        UUID invoiceId = UUID.randomUUID();
        Path dir = Path.of("data/uploads");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(invoiceId + ".pdf");
            Files.write(file, pdf);
            return new StoredPdf(invoiceId, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @Transactional
    public SuggestionId persist(StoredPdf stored, ExtractedInvoice extracted, List<Posting> postings,
                                ModelRun extraction, ModelRun mapping) {
        UUID invoiceId = stored.invoiceId();

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
                stored.pdfPath());

        UUID extractionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO extractions (id, invoice_id, raw_json, model, prompt_version, latency_ms)
                VALUES (?,?,?,?,?,?)
                """,
                extractionId, invoiceId, toJson(extracted),
                extraction.model(), extraction.promptVersion(), extraction.latencyMs());

        UUID suggestionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO suggestions (id, invoice_id, extraction_id, model, prompt_version, latency_ms)
                VALUES (?,?,?,?,?,?)
                """,
                suggestionId, invoiceId, extractionId,
                mapping.model(), mapping.promptVersion(), mapping.latencyMs());

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

        emitAudit("suggestion", suggestionId, "suggestion.created",
                Map.of("invoiceId", invoiceId.toString(), "lineCount", postings.size()));

        return new SuggestionId(suggestionId);
    }

    @Override
    @Transactional
    public DecisionResponse recordDecision(SuggestionId suggestionId, DecisionStatus status, String note) {
        DecisionResponse echo;
        try {
            echo = jdbc.queryForObject("""
                    INSERT INTO decisions (suggestion_id, status, note)
                    VALUES (?,?,?)
                    ON CONFLICT (suggestion_id) DO UPDATE
                        SET status = EXCLUDED.status, note = EXCLUDED.note, decided_at = NOW()
                    RETURNING status, decided_at, note
                    """,
                    (rs, rowNum) -> new DecisionResponse(
                            rs.getString("status"),
                            rs.getTimestamp("decided_at").toInstant(),
                            rs.getString("note")),
                    suggestionId.value(), status.name(), note);
        } catch (DataIntegrityViolationException e) {
            throw new NotFoundException("suggestion not found: " + suggestionId.value());
        }

        emitAudit("suggestion", suggestionId.value(), "decision." + status.name().toLowerCase(),
                note != null ? Map.of("note", note) : Map.of());

        return echo;
    }

    private void emitAudit(String entity, UUID entityId, String event, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO audit_events (entity, entity_id, event, payload_json)
                VALUES (?,?,?,?)
                """,
                entity, entityId, event, toJson(payload));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
