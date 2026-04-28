package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.*;
import com.sturdywaffle.domain.port.Extractor;
import com.sturdywaffle.domain.port.Mapper;
import com.sturdywaffle.domain.service.Assembler;
import com.sturdywaffle.domain.service.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    private final Extractor extractor;
    private final List<Mapper> mappers;
    private final Validator validator = new Validator();
    private final Assembler assembler = new Assembler();

    @Autowired(required = false)
    private Persister persister;

    public PipelineService(Extractor extractor, List<Mapper> mappers) {
        this.extractor = extractor;
        this.mappers = mappers;
    }

    public SuggestionId run(byte[] pdf, String originalFilename) {
        long extractStart = System.currentTimeMillis();
        ExtractedInvoice extracted = extractor.extract(pdf);
        long extractionLatencyMs = System.currentTimeMillis() - extractStart;

        validator.validate(extracted);

        long mapStart = System.currentTimeMillis();
        List<MappingProposal> proposals = mapLines(extracted);
        long mappingLatencyMs = System.currentTimeMillis() - mapStart;

        List<Posting> postings = assembler.assemble(extracted, proposals);

        Mapper primaryMapper = mappers.get(0);
        return persister.persist(pdf, extracted, postings,
                extractor.modelId(), extractor.promptVersion(), extractionLatencyMs,
                primaryMapper.modelId(), primaryMapper.promptVersion(), mappingLatencyMs);
    }

    public EvalResult evaluate(byte[] pdf) {
        long start = System.currentTimeMillis();
        ExtractedInvoice extracted = extractor.extract(pdf);
        validator.validate(extracted);
        List<MappingProposal> proposals = mapLines(extracted);
        List<Posting> postings = assembler.assemble(extracted, proposals);
        return new EvalResult(extracted, postings, System.currentTimeMillis() - start);
    }

    private List<MappingProposal> mapLines(ExtractedInvoice extracted) {
        return extracted.lines().stream()
                .map(line -> mappers.stream()
                        .flatMap(m -> m.map(extracted.supplierName(), line).stream())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No mapper could handle line: " + line.description())))
                .toList();
    }
}
