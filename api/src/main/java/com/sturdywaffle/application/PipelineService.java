package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.*;
import com.sturdywaffle.domain.port.Extractor;
import com.sturdywaffle.domain.port.Mapper;
import com.sturdywaffle.domain.service.Assembler;
import com.sturdywaffle.domain.service.Validator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    private final Extractor extractor;
    private final List<Mapper> mappers;
    private final Persister persister;
    private final Validator validator = new Validator();
    private final Assembler assembler = new Assembler();

    public PipelineService(Extractor extractor, List<Mapper> mappers, Persister persister) {
        this.extractor = extractor;
        this.mappers = mappers;
        this.persister = persister;
    }

    public SuggestionId run(byte[] pdf, String originalFilename) {
        PipelineRun run = process(pdf);
        Persister.StoredPdf stored = persister.storePdf(pdf);
        Mapper primaryMapper = mappers.get(0);
        return persister.persist(stored, run.extracted, run.postings,
                new ModelRun(extractor.modelId(), extractor.promptVersion(), run.extractionLatencyMs),
                new ModelRun(primaryMapper.modelId(), primaryMapper.promptVersion(), run.mappingLatencyMs));
    }

    public EvalResult evaluate(byte[] pdf) {
        PipelineRun run = process(pdf);
        return new EvalResult(run.extracted, run.postings, run.extractionLatencyMs + run.mappingLatencyMs);
    }

    private PipelineRun process(byte[] pdf) {
        long extractStart = System.currentTimeMillis();
        ExtractedInvoice extracted = extractor.extract(pdf);
        long extractionLatencyMs = System.currentTimeMillis() - extractStart;

        validator.validate(extracted);

        long mapStart = System.currentTimeMillis();
        List<MappingProposal> proposals = extracted.lines().stream()
                .map(line -> mappers.stream()
                        .flatMap(m -> m.map(extracted.supplierName(), line).stream())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No mapper could handle line: " + line.description())))
                .toList();
        long mappingLatencyMs = System.currentTimeMillis() - mapStart;

        List<Posting> postings = assembler.assemble(extracted, proposals);
        return new PipelineRun(extracted, postings, extractionLatencyMs, mappingLatencyMs);
    }

    private record PipelineRun(ExtractedInvoice extracted, List<Posting> postings,
                                long extractionLatencyMs, long mappingLatencyMs) {}
}
