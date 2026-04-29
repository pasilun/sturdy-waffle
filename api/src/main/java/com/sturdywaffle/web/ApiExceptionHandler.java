package com.sturdywaffle.web;

import com.sturdywaffle.domain.exception.ConflictException;
import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.exception.NotFoundException;
import com.sturdywaffle.domain.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
        log.warn("validation_failed: {}", ex.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "validation_failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<Map<String, String>> handleExtraction(ExtractionException ex) {
        log.warn("extraction_failed: {}", ex.getMessage(), ex);
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "extraction_failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        log.warn("not_found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "detail", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        log.warn("conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "conflict", "detail", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadRequestBody(HttpMessageNotReadableException ex) {
        log.warn("bad_request: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "detail",
                        ex.getMostSpecificCause().getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Throwable ex) {
        // Catch-all so 500s never go silent. Spring's default 500 handler
        // doesn't always log the exception class — this guarantees one line
        // per unhandled error in our format.
        log.error("internal_error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_error", "detail", ex.getClass().getSimpleName()));
    }
}
