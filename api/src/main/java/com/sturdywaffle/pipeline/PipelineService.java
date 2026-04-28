package com.sturdywaffle.pipeline;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates: store → extract → validate → map → assemble → persist.
 * Phase 1: stub that returns a placeholder SuggestionId.
 */
@Service
public class PipelineService {

    public SuggestionId run(byte[] pdf, String originalFilename) {
        // TODO Phase 2: store PDF, call extractor, validate, map, assemble, persist
        return new SuggestionId(UUID.randomUUID());
    }
}
