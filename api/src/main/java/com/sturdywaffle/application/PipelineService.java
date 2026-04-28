package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.*;
import com.sturdywaffle.domain.port.Extractor;
import com.sturdywaffle.domain.port.Mapper;
import com.sturdywaffle.domain.service.Assembler;
import com.sturdywaffle.domain.service.Validator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PipelineService {

    private final Extractor extractor;
    private final List<Mapper> mappers;
    private final Validator validator = new Validator();
    private final Assembler assembler = new Assembler();

    public PipelineService(Extractor extractor, List<Mapper> mappers) {
        this.extractor = extractor;
        this.mappers = mappers;
    }

    public SuggestionId run(byte[] pdf, String originalFilename) {
        ExtractedInvoice extracted = extractor.extract(pdf);
        validator.validate(extracted);

        List<MappingProposal> proposals = extracted.lines().stream()
                .map(line -> mappers.stream()
                        .flatMap(m -> m.map(extracted.supplierName(), line).stream())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No mapper could handle line: " + line.description())))
                .toList();

        List<Posting> postings = assembler.assemble(extracted, proposals);

        // Phase 3: persist to DB; for now return a stable placeholder
        return new SuggestionId(UUID.randomUUID());
    }
}
