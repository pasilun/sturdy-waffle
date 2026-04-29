package com.sturdywaffle.web;

import com.sturdywaffle.domain.exception.ConflictException;
import com.sturdywaffle.domain.exception.ExtractionException;
import com.sturdywaffle.domain.exception.NotFoundException;
import com.sturdywaffle.domain.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "detail", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "conflict", "detail", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadRequestBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "detail",
                        ex.getMostSpecificCause().getMessage()));
    }
}
