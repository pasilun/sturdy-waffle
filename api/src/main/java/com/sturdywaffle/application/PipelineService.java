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
        process(pdf);
        return new SuggestionId(UUID.randomUUID());
    }

    public EvalResult evaluate(byte[] pdf) {
        long start = System.currentTimeMillis();
        PipelineOutput out = process(pdf);
        return new EvalResult(out.extracted(), out.postings(), System.currentTimeMillis() - start);
    }

    private PipelineOutput process(byte[] pdf) {
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
        return new PipelineOutput(extracted, postings);
    }

    private record PipelineOutput(ExtractedInvoice extracted, List<Posting> postings) {}
}
