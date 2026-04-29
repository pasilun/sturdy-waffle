package com.sturdywaffle.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sturdywaffle.application.Persister;
import com.sturdywaffle.domain.exception.ConflictException;
import com.sturdywaffle.domain.exception.NotFoundException;
import com.sturdywaffle.domain.model.DecisionStatus;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.ModelRun;
import com.sturdywaffle.domain.model.Money;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.web.dto.SuggestionResponse;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real Postgres via embedded-postgres on a random port — same engine as
// prod, so SQL behaviour (NULL parameter typing, NUMERIC scale,
// constraint violations) is observable. See [[tests-protect-invariants-not-implementation]]
// for why this beats mocking JdbcTemplate.
class JdbcPersisterIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ObjectMapper objectMapper;
    private static Persister persister;
    private static InvoiceListQuery listQuery;
    private static SuggestionQuery suggestionQuery;

    @BeforeAll
    static void bootEmbeddedPostgres() throws IOException {
        Path dataDir = Files.createTempDirectory("jdbc-persister-it-pg");
        embeddedPostgres = EmbeddedPostgres.builder()
                .setPort(0)                 // random free port; no collision with dev.sh
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(true)
                .start();
        dataSource = embeddedPostgres.getPostgresDatabase();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        jdbc         = new JdbcTemplate(dataSource);
        persister       = new JdbcPersister(jdbc, objectMapper);
        listQuery       = new InvoiceListQuery(jdbc);
        suggestionQuery = new SuggestionQuery(jdbc);

        seedAccounts();
    }

    @AfterAll
    static void shutdown() throws IOException {
        if (embeddedPostgres != null) embeddedPostgres.close();
    }

    @BeforeEach
    void truncate() {
        // Order respects FK chains. accounts stays seeded.
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM decisions");
        jdbc.update("DELETE FROM postings");
        jdbc.update("DELETE FROM suggestions");
        jdbc.update("DELETE FROM extractions");
        jdbc.update("DELETE FROM invoices");
    }

    // ── persist + roundtrip ────────────────────────────────────────────

    @Test
    void persistRoundtripsThroughSuggestionQuery() {
        // 8000 net + 25% vat = 2000 vat, 10000 gross.
        SuggestionId id = persistFixture(line("Rent", "8000.00"), "5010");

        Optional<SuggestionResponse> fetched = suggestionQuery.findById(id.value());

        assertTrue(fetched.isPresent());
        SuggestionResponse s = fetched.get();
        assertEquals("ACME AB", s.supplierName());
        // Money.toString returns the canonical scale-2 form; fetched value
        // should be the same string regardless of how Postgres rendered
        // the underlying NUMERIC. Pinned because [[bigdecimal-scale-equality]].
        assertEquals("10000.00", s.gross());
        assertEquals(3, s.postings().size(), "1 line + 2 synthetic");
    }

    @Test
    void persistEmitsSuggestionCreatedAuditRow() {
        SuggestionId id = persistFixture(line("Rent", "8000.00"), "5010");

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events
                WHERE entity_id = ? AND event = 'suggestion.created'
                """, Integer.class, id.value());

        assertEquals(1, auditCount);
    }

    // ── replaceMapping happy path ─────────────────────────────────────

    @Test
    void replaceMappingSwapsPostingsAndUpdatesSuggestionsModel() {
        SuggestionId id = persistFixture(line("Slack", "500.00"), "6540");

        // Sanity — the original mapping says 6540
        String originalAccount = jdbc.queryForObject(
                "SELECT account_code FROM postings WHERE suggestion_id = ? AND line_index = 0",
                String.class, id.value());
        assertEquals("6540", originalAccount);

        List<Posting> newPostings = List.of(
                new Posting(0, "6560", Money.of("500.00"), null,
                        "Slack", "Subscription software", 0.97),
                new Posting(-1, "2640", Money.of("125.00"), null, "Ingående moms", null, null),
                new Posting(-1, "2440", null, Money.of("625.00"),
                        "Leverantörsskuld ACME AB", null, null));

        persister.replaceMapping(id, newPostings,
                new ModelRun("claude-sonnet-4-6", "map.v1.escalation", 1234L));

        String newAccount = jdbc.queryForObject(
                "SELECT account_code FROM postings WHERE suggestion_id = ? AND line_index = 0",
                String.class, id.value());
        assertEquals("6560", newAccount);

        String suggestionsModel = jdbc.queryForObject(
                "SELECT model FROM suggestions WHERE id = ?",
                String.class, id.value());
        assertEquals("claude-sonnet-4-6", suggestionsModel);
    }

    @Test
    void replaceMappingEmitsAuditRowWithFromAndToModel() {
        SuggestionId id = persistFixture(line("Slack", "500.00"), "6540");

        persister.replaceMapping(id,
                rebalancedPostings("6560", "500.00", "125.00", "625.00", "ACME AB"),
                new ModelRun("claude-sonnet-4-6", "map.v1.escalation", 1234L));

        String payloadJson = jdbc.queryForObject("""
                SELECT payload_json FROM audit_events
                WHERE entity_id = ? AND event = 'mapping.escalated'
                """, String.class, id.value());

        assertNotNull(payloadJson);
        Map<String, Object> payload = readJson(payloadJson);
        assertEquals("claude-haiku-4-5", payload.get("fromModel"),
                "fromModel records what the suggestion's model column was before the swap");
        assertEquals("claude-sonnet-4-6", payload.get("toModel"));
        assertEquals(3, ((Number) payload.get("lineCount")).intValue());
    }

    // ── replaceMapping lock ───────────────────────────────────────────

    @Test
    void replaceMappingThrowsConflictWhenAlreadyDecided() {
        SuggestionId id = persistFixture(line("Slack", "500.00"), "6540");

        persister.recordDecision(id, DecisionStatus.APPROVED, null);

        assertThrows(ConflictException.class,
                () -> persister.replaceMapping(id,
                        rebalancedPostings("6560", "500.00", "125.00", "625.00", "ACME AB"),
                        new ModelRun("claude-sonnet-4-6", "map.v1.escalation", 1L)));

        // Postings should still be the originals — the conflict check
        // happens before any mutation.
        String stillOriginal = jdbc.queryForObject(
                "SELECT account_code FROM postings WHERE suggestion_id = ? AND line_index = 0",
                String.class, id.value());
        assertEquals("6540", stillOriginal);
    }

    @Test
    void replaceMappingThrowsNotFoundForUnknownSuggestion() {
        SuggestionId missing = new SuggestionId(java.util.UUID.randomUUID());

        assertThrows(NotFoundException.class,
                () -> persister.replaceMapping(missing,
                        rebalancedPostings("6560", "500.00", "125.00", "625.00", "ACME AB"),
                        new ModelRun("claude-sonnet-4-6", "map.v1.escalation", 1L)));
    }

    // ── decision audit ────────────────────────────────────────────────

    @Test
    void recordDecisionEmitsAuditRowKeyedToStatus() {
        SuggestionId id = persistFixture(line("Rent", "8000.00"), "5010");

        persister.recordDecision(id, DecisionStatus.APPROVED, "looks good");

        Integer approvedAudit = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events
                WHERE entity_id = ? AND event = 'decision.approved'
                """, Integer.class, id.value());
        assertEquals(1, approvedAudit);

        // Idempotency: approving again upserts the decision and emits a
        // fresh audit row (one decision row, two audit rows total).
        persister.recordDecision(id, DecisionStatus.APPROVED, "still looks good");
        Integer auditAgain = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events
                WHERE entity_id = ? AND event = 'decision.approved'
                """, Integer.class, id.value());
        assertEquals(2, auditAgain);
    }

    // ── InvoiceListQuery — Phase 5 NULL-typing regression ─────────────

    @Test
    void listWithNullStatusFilterReturnsEverything() {
        // Phase 5 bug: a single SQL with `WHERE (? IS NULL OR ...)` failed
        // because Postgres can't infer the parameter type when both branches
        // of the OR are conditional. Fix was to emit two distinct SQL strings.
        // This test pins that fix forever.
        persistFixture(line("Rent", "8000.00"), "5010");
        persistFixture(line("Slack", "500.00"), "6540");

        var rows = listQuery.list(null, 100);

        assertEquals(2, rows.size());
    }

    @Test
    void listWithApprovedStatusFilterRespectsDecision() {
        SuggestionId approved = persistFixture(line("Rent", "8000.00"), "5010");
        persistFixture(line("Slack", "500.00"), "6540"); // pending

        persister.recordDecision(approved, DecisionStatus.APPROVED, null);

        var rows = listQuery.list("APPROVED", 100);
        assertEquals(1, rows.size());
        assertEquals(approved.value(), rows.get(0).suggestionId());
    }

    @Test
    void listWithPendingStatusFilterShowsUndecided() {
        SuggestionId approved = persistFixture(line("Rent", "8000.00"), "5010");
        persistFixture(line("Slack", "500.00"), "6540");

        persister.recordDecision(approved, DecisionStatus.APPROVED, null);

        var rows = listQuery.list("PENDING", 100);
        assertEquals(1, rows.size());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static void seedAccounts() {
        try (InputStream is = new ClassPathResource("seed/chart.json").getInputStream()) {
            List<Map<String, String>> accounts =
                    objectMapper.readValue(is, new TypeReference<>() {});
            for (Map<String, String> a : accounts) {
                jdbc.update("""
                        INSERT INTO accounts (code, name, type, normal_side)
                        VALUES (?,?,?,?)
                        ON CONFLICT (code) DO NOTHING
                        """,
                        a.get("code"), a.get("name"), a.get("type"), a.get("normalSide"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to seed accounts", e);
        }
    }

    private SuggestionId persistFixture(InvoiceLine line, String accountCode) {
        Money net = line.net();
        Money vat = Money.of(net.value().multiply(new java.math.BigDecimal("0.25")));
        Money gross = net.add(vat);

        ExtractedInvoice extracted = new ExtractedInvoice(
                "ACME AB", "INV-1", LocalDate.of(2026, 4, 1), "SEK",
                List.of(line), net, vat, gross);

        Persister.StoredPdf stored = new Persister.StoredPdf(
                java.util.UUID.randomUUID(), "/tmp/test.pdf");

        List<Posting> postings = List.of(
                new Posting(0, accountCode, net, null,
                        line.description(), "test reasoning", 0.9),
                new Posting(-1, "2640", vat, null, "Ingående moms", null, null),
                new Posting(-1, "2440", null, gross,
                        "Leverantörsskuld ACME AB", null, null));

        return persister.persist(stored, extracted, postings,
                new ModelRun("claude-sonnet-4-6", "extract.v1", 100L),
                new ModelRun("claude-haiku-4-5", "map.v1", 200L));
    }

    private static InvoiceLine line(String desc, String net) {
        return new InvoiceLine(desc, Money.of(net));
    }

    private static List<Posting> rebalancedPostings(String accountCode, String net, String vat, String gross, String supplier) {
        return List.of(
                new Posting(0, accountCode, Money.of(net), null,
                        "line", "rationale", 0.95),
                new Posting(-1, "2640", Money.of(vat), null, "Ingående moms", null, null),
                new Posting(-1, "2440", null, Money.of(gross),
                        "Leverantörsskuld " + supplier, null, null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
