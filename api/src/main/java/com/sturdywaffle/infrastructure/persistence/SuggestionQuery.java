package com.sturdywaffle.infrastructure.persistence;

import com.sturdywaffle.web.dto.DecisionResponse;
import com.sturdywaffle.web.dto.PostingResponse;
import com.sturdywaffle.web.dto.SuggestionResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!eval")
public class SuggestionQuery {

    private final JdbcTemplate jdbc;

    public SuggestionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SuggestionResponse> findById(UUID suggestionId) {
        List<SuggestionResponse> rows = jdbc.query("""
                SELECT s.id AS suggestion_id, i.id AS invoice_id,
                       i.supplier_name, i.invoice_number, i.invoice_date,
                       i.currency, i.net, i.vat, i.gross,
                       d.status AS decision_status, d.decided_at, d.note AS decision_note
                FROM suggestions s
                JOIN invoices i ON i.id = s.invoice_id
                LEFT JOIN decisions d ON d.suggestion_id = s.id
                WHERE s.id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp decidedAt = rs.getTimestamp("decided_at");
                    DecisionResponse decision = rs.getString("decision_status") != null
                            ? new DecisionResponse(
                                    rs.getString("decision_status"),
                                    decidedAt != null ? decidedAt.toInstant().toString() : null,
                                    rs.getString("decision_note"))
                            : null;
                    return new SuggestionResponse(
                            UUID.fromString(rs.getString("suggestion_id")),
                            UUID.fromString(rs.getString("invoice_id")),
                            rs.getString("supplier_name"),
                            rs.getString("invoice_number"),
                            rs.getDate("invoice_date") != null ? rs.getDate("invoice_date").toString() : null,
                            rs.getString("currency"),
                            rs.getBigDecimal("net") != null ? rs.getBigDecimal("net").toPlainString() : null,
                            rs.getBigDecimal("vat") != null ? rs.getBigDecimal("vat").toPlainString() : null,
                            rs.getBigDecimal("gross") != null ? rs.getBigDecimal("gross").toPlainString() : null,
                            List.of(),
                            decision);
                },
                suggestionId);

        if (rows.isEmpty()) return Optional.empty();

        SuggestionResponse base = rows.get(0);
        List<PostingResponse> postings = fetchPostings(suggestionId);
        return Optional.of(new SuggestionResponse(
                base.id(), base.invoiceId(), base.supplierName(), base.invoiceNumber(),
                base.invoiceDate(), base.currency(), base.net(), base.vat(), base.gross(),
                postings, base.decision()));
    }

    public Optional<String> findPdfPath(UUID suggestionId) {
        List<String> rows = jdbc.query("""
                SELECT i.pdf_path
                FROM suggestions s
                JOIN invoices i ON i.id = s.invoice_id
                WHERE s.id = ?
                """,
                (rs, rowNum) -> rs.getString("pdf_path"),
                suggestionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<PostingResponse> fetchPostings(UUID suggestionId) {
        return jdbc.query("""
                SELECT p.line_index, p.account_code, a.name AS account_name,
                       p.debit, p.credit, p.description, p.reasoning, p.confidence
                FROM postings p
                JOIN accounts a ON a.code = p.account_code
                WHERE p.suggestion_id = ?
                ORDER BY p.line_index
                """,
                (rs, rowNum) -> new PostingResponse(
                        rs.getInt("line_index"),
                        rs.getString("account_code"),
                        rs.getString("account_name"),
                        rs.getBigDecimal("debit") != null ? rs.getBigDecimal("debit").toPlainString() : null,
                        rs.getBigDecimal("credit") != null ? rs.getBigDecimal("credit").toPlainString() : null,
                        rs.getString("description"),
                        rs.getString("reasoning"),
                        rs.getObject("confidence") != null ? rs.getDouble("confidence") : null),
                suggestionId);
    }
}
