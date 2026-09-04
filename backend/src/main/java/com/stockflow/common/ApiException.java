package com.stockflow.common;

import org.springframework.http.HttpStatus;

/**
 * Thrown by services for business-rule violations; mapped to an HTTP status
 * by GlobalExceptionHandler. Keeps controllers free of try/catch noise.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String field;
    private final String fieldMessage;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    public ApiException(HttpStatus status, String message, String field, String fieldMessage) {
        super(message);
        this.status = status;
        this.field = field;
        this.fieldMessage = fieldMessage;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message, String field, String fieldMessage) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, field, fieldMessage);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }

    public String getFieldMessage() {
        return fieldMessage;
    }
}
