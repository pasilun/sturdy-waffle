package com.sturdywaffle.infrastructure.persistence;

import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.web.dto.DecisionResponse;
import com.sturdywaffle.web.dto.PostingResponse;
import com.sturdywaffle.web.dto.SuggestionResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        List<PostingResponse> postings = fetchPostings(suggestionId);
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
                (rs, rowNum) -> new SuggestionResponse(
                        rs.getObject("suggestion_id", UUID.class),
                        rs.getObject("invoice_id", UUID.class),
                        rs.getString("supplier_name"),
                        rs.getString("invoice_number"),
                        rs.getObject("invoice_date", java.time.LocalDate.class),
                        rs.getString("currency"),
                        moneyStr(rs, "net"),
                        moneyStr(rs, "vat"),
                        moneyStr(rs, "gross"),
                        postings,
                        decisionFrom(rs)),
                suggestionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
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
                        moneyStr(rs, "debit"),
                        moneyStr(rs, "credit"),
                        rs.getString("description"),
                        rs.getString("reasoning"),
                        rs.getObject("confidence") != null ? rs.getDouble("confidence") : null),
                suggestionId);
    }

    private static DecisionResponse decisionFrom(ResultSet rs) throws SQLException {
        String status = rs.getString("decision_status");
        if (status == null) return null;
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new DecisionResponse(
                status,
                decidedAt != null ? decidedAt.toInstant() : null,
                rs.getString("decision_note"));
    }

    private static String moneyStr(ResultSet rs, String col) throws SQLException {
        BigDecimal bd = rs.getBigDecimal(col);
        return bd != null ? Money.of(bd).toString() : null;
    }
}
