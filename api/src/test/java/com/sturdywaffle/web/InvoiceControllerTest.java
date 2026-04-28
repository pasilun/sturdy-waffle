package com.sturdywaffle.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sturdywaffle.application.Persister;
import com.sturdywaffle.application.PipelineService;
import com.sturdywaffle.domain.exception.NotFoundException;
import com.sturdywaffle.domain.model.DecisionStatus;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.infrastructure.persistence.SuggestionQuery;
import com.sturdywaffle.web.dto.DecisionResponse;
import com.sturdywaffle.web.dto.SuggestionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PipelineService pipelineService;
    @MockitoBean SuggestionQuery suggestionQuery;
    @MockitoBean Persister persister;

    @Test
    void getReturns404WhenSuggestionMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestionQuery.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void getReturnsSuggestionResponseWhenFound() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        SuggestionResponse body = new SuggestionResponse(
                suggestionId, invoiceId, "ACME AB", "INV-1",
                LocalDate.of(2026, 4, 1), "SEK", "100.00", "25.00", "125.00",
                List.of(), null);
        when(suggestionQuery.findById(suggestionId)).thenReturn(Optional.of(body));

        mockMvc.perform(get("/invoices/{id}", suggestionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(suggestionId.toString()))
                .andExpect(jsonPath("$.invoiceDate").value("2026-04-01"))
                .andExpect(jsonPath("$.gross").value("125.00"));
    }

    @Test
    void decisionEchoesPersistedRow() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-04-28T12:00:00Z");
        when(persister.recordDecision(eq(new SuggestionId(suggestionId)), eq(DecisionStatus.APPROVED), any()))
                .thenReturn(new DecisionResponse("APPROVED", decidedAt, "looks good"));

        mockMvc.perform(post("/invoices/{id}/decision", suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED","note":"looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").value("2026-04-28T12:00:00Z"))
                .andExpect(jsonPath("$.note").value("looks good"));

        verify(persister).recordDecision(new SuggestionId(suggestionId), DecisionStatus.APPROVED, "looks good");
    }

    @Test
    void decisionRejectsUnknownStatusAs400() throws Exception {
        UUID suggestionId = UUID.randomUUID();

        mockMvc.perform(post("/invoices/{id}/decision", suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"MAYBE","note":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void decisionReturns404WhenSuggestionMissing() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        when(persister.recordDecision(any(), any(), any()))
                .thenThrow(new NotFoundException("suggestion not found: " + suggestionId));

        mockMvc.perform(post("/invoices/{id}/decision", suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DECLINED","note":null}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void getPdfReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestionQuery.findPdfPath(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/{id}/pdf", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
