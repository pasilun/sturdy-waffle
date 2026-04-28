package com.sturdywaffle.web;

import com.sturdywaffle.application.Persister;
import com.sturdywaffle.application.PipelineService;
import com.sturdywaffle.domain.model.SuggestionId;
import com.sturdywaffle.infrastructure.persistence.SuggestionQuery;
import com.sturdywaffle.web.dto.DecisionRequest;
import com.sturdywaffle.web.dto.SuggestionResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@Profile("!eval")
public class InvoiceController {

    private final PipelineService pipelineService;
    private final SuggestionQuery suggestionQuery;
    private final Persister persister;

    public InvoiceController(PipelineService pipelineService,
                              SuggestionQuery suggestionQuery,
                              Persister persister) {
        this.pipelineService = pipelineService;
        this.suggestionQuery = suggestionQuery;
        this.persister = persister;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        SuggestionId id = pipelineService.run(file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("id", id.value().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuggestionResponse> get(@PathVariable UUID id) {
        return suggestionQuery.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable UUID id) throws IOException {
        var pdfPath = suggestionQuery.findPdfPath(id);
        if (pdfPath.isEmpty()) return ResponseEntity.notFound().build();
        byte[] bytes = Files.readAllBytes(Path.of(pdfPath.get()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Void> decide(@PathVariable UUID id,
                                       @RequestBody DecisionRequest body) {
        persister.recordDecision(id, body.status(), body.note());
        return ResponseEntity.ok().build();
    }
}
