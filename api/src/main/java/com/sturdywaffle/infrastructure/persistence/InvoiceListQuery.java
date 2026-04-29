package com.sturdywaffle.infrastructure.persistence;

import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.web.dto.InvoiceListItem;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!eval")
public class InvoiceListQuery {

    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbc;

    public InvoiceListQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InvoiceListItem> list(String statusFilter, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return jdbc.query("""
                SELECT s.id AS suggestion_id, i.id AS invoice_id,
                       i.supplier_name, i.invoice_number, i.invoice_date,
                       i.currency, i.gross, i.created_at,
                       COALESCE(d.status, 'PENDING') AS status, d.decided_at
                FROM suggestions s
                JOIN invoices i ON i.id = s.invoice_id
                LEFT JOIN decisions d ON d.suggestion_id = s.id
                WHERE (? IS NULL OR COALESCE(d.status, 'PENDING') = ?)
                ORDER BY s.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                statusFilter, statusFilter, safeLimit);
    }

    private static InvoiceListItem mapRow(ResultSet rs) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new InvoiceListItem(
                rs.getObject("suggestion_id", UUID.class),
                rs.getObject("invoice_id", UUID.class),
                rs.getString("supplier_name"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", java.time.LocalDate.class),
                rs.getString("currency"),
                moneyStr(rs, "gross"),
                rs.getString("status"),
                decidedAt != null ? decidedAt.toInstant() : null,
                createdAt != null ? createdAt.toInstant() : null);
    }

    private static String moneyStr(ResultSet rs, String col) throws SQLException {
        BigDecimal bd = rs.getBigDecimal(col);
        return bd != null ? Money.of(bd).toString() : null;
    }
}
