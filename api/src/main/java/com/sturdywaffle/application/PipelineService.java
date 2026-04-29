package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.*;
import com.sturdywaffle.domain.port.Extractor;
import com.sturdywaffle.domain.port.Mapper;
import com.sturdywaffle.domain.service.Assembler;
import com.sturdywaffle.domain.service.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    private final Extractor extractor;
    private final Mapper primaryMapper;
    private final Mapper escalationMapper;
    private final Persister persister;
    private final Validator validator = new Validator();
    private final Assembler assembler = new Assembler();

    public PipelineService(Extractor extractor,
                           @Qualifier("primaryMapper") Mapper primaryMapper,
                           @Qualifier("escalationMapper") Mapper escalationMapper,
                           Persister persister) {
        this.extractor = extractor;
        this.primaryMapper = primaryMapper;
        this.escalationMapper = escalationMapper;
        this.persister = persister;
    }

    public SuggestionId run(byte[] pdf, String originalFilename) {
        PipelineRun run = process(pdf, primaryMapper);
        Persister.StoredPdf stored = persister.storePdf(pdf);
        return persister.persist(stored, run.extracted, run.postings,
                new ModelRun(extractor.modelId(), extractor.promptVersion(), run.extractionLatencyMs),
                new ModelRun(primaryMapper.modelId(), primaryMapper.promptVersion(), run.mappingLatencyMs));
    }

    public SuggestionId escalate(SuggestionId suggestionId) {
        ExtractedInvoice extracted = persister.loadExtractedInvoice(suggestionId);
        PipelineRun run = remap(extracted, escalationMapper);
        persister.replaceMapping(suggestionId, run.postings,
                new ModelRun(escalationMapper.modelId(), escalationMapper.promptVersion(), run.mappingLatencyMs));
        return suggestionId;
    }

    public EvalResult evaluate(byte[] pdf) {
        PipelineRun run = process(pdf, primaryMapper);
        return new EvalResult(run.extracted, run.postings, run.extractionLatencyMs + run.mappingLatencyMs);
    }

    private PipelineRun process(byte[] pdf, Mapper mapper) {
        long extractStart = System.currentTimeMillis();
        ExtractedInvoice extracted = extractor.extract(pdf);
        long extractionLatencyMs = System.currentTimeMillis() - extractStart;

        validator.validate(extracted);

        PipelineRun mapped = remap(extracted, mapper);
        return new PipelineRun(extracted, mapped.postings, extractionLatencyMs, mapped.mappingLatencyMs);
    }

    private PipelineRun remap(ExtractedInvoice extracted, Mapper mapper) {
        long mapStart = System.currentTimeMillis();
        List<MappingProposal> proposals = extracted.lines().stream()
                .map(line -> mapper.map(extracted.supplierName(), line)
                        .orElseThrow(() -> new IllegalStateException(
                                "Mapper could not handle line: " + line.description())))
                .toList();
        long mappingLatencyMs = System.currentTimeMillis() - mapStart;

        List<Posting> postings = assembler.assemble(extracted, proposals);
        return new PipelineRun(extracted, postings, 0, mappingLatencyMs);
    }

    private record PipelineRun(ExtractedInvoice extracted, List<Posting> postings,
                                long extractionLatencyMs, long mappingLatencyMs) {}
}
