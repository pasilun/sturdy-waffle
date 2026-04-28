package com.sturdywaffle.web;

import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.exception.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "validation_failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<Map<String, String>> handleExtraction(ExtractionException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "extraction_failed", "detail", ex.getMessage()));
    }
}
