package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.DecisionStatus;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.ModelRun;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.web.dto.DecisionResponse;

import java.util.List;
import java.util.UUID;

public interface Persister {

    record StoredPdf(UUID invoiceId, String pdfPath) {}

    StoredPdf storePdf(byte[] pdf);

    SuggestionId persist(StoredPdf stored, ExtractedInvoice extracted, List<Posting> postings,
                         ModelRun extraction, ModelRun mapping);

    DecisionResponse recordDecision(SuggestionId suggestionId, DecisionStatus status, String note);

    ExtractedInvoice loadExtractedInvoice(SuggestionId suggestionId);

    void replaceMapping(SuggestionId suggestionId, List<Posting> postings, ModelRun mapping);
}
