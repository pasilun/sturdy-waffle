package com.sturdywaffle.eval;

import com.sturdywaffle.application.Persister;
import com.sturdywaffle.domain.model.DecisionStatus;
import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.ModelRun;
import com.sturdywaffle.domain.model.Posting;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.web.dto.DecisionResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("eval")
public class EvalConfig {

    @Bean
    Persister noopPersister() {
        return new Persister() {
            @Override
            public StoredPdf storePdf(byte[] pdf) {
                throw new UnsupportedOperationException("eval profile does not persist");
            }
            @Override
            public SuggestionId persist(StoredPdf stored, ExtractedInvoice extracted, List<Posting> postings,
                                        ModelRun extraction, ModelRun mapping) {
                throw new UnsupportedOperationException("eval profile does not persist");
            }
            @Override
            public DecisionResponse recordDecision(SuggestionId suggestionId, DecisionStatus status, String note) {
                throw new UnsupportedOperationException("eval profile does not persist");
            }
            @Override
            public ExtractedInvoice loadExtractedInvoice(SuggestionId suggestionId) {
                throw new UnsupportedOperationException("eval profile does not persist");
            }
            @Override
            public void replaceMapping(SuggestionId suggestionId, List<Posting> postings, ModelRun mapping) {
                throw new UnsupportedOperationException("eval profile does not persist");
            }
        };
    }
}
