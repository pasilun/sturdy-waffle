package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;

import java.util.List;
import java.util.UUID;

public interface Persister {
    SuggestionId persist(byte[] pdf, ExtractedInvoice extracted, List<Posting> postings,
                         String extractorModel, String extractorPromptVersion, long extractionLatencyMs,
                         String mapperModel, String mapperPromptVersion, long mappingLatencyMs);

    void recordDecision(UUID suggestionId, String status, String note);
}
