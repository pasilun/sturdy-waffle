package com.sturdywaffle.infrastructure.persistence;

import com.sturdywaffle.web.dto.ActivityResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!eval")
public class ActivityQuery {

    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbc;

    public ActivityQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ActivityResponse> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return jdbc.query("""
                SELECT id, entity_id, event, payload_json, created_at
                FROM audit_events
                WHERE event IN ('suggestion.created','decision.approved','decision.declined')
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new ActivityResponse(
                            rs.getObject("id", UUID.class),
                            rs.getString("event"),
                            rs.getObject("entity_id", UUID.class),
                            rs.getString("payload_json"),
                            createdAt != null ? createdAt.toInstant() : null);
                },
                safeLimit);
    }
}
