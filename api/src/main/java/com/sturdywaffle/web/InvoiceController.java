package com.sturdywaffle.web;

import com.sturdywaffle.application.PipelineService;
import com.sturdywaffle.domain.model.SuggestionId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final PipelineService pipelineService;

    public InvoiceController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        SuggestionId id = pipelineService.run(file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("id", id.value().toString()));
    }
}
