package com.stockflow.common;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Predictable error shape for every non-2xx response:
 * { status, error, message, fieldErrors?, timestamp }
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, null, Instant.now());
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, fieldErrors, Instant.now());
    }

    public static ErrorResponse of(HttpStatus status, String message, String field, String fieldMessage) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, fieldMessage);
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, fields, Instant.now());
    }
}
