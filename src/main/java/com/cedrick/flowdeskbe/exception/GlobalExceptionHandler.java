package com.cedrick.flowdeskbe.exception;

import com.cedrick.flowdeskbe.dto.ApiResponse;
import com.cedrick.flowdeskbe.dto.FieldErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Single place where every exception becomes the uniform {@link ApiResponse} error shape.
 * Extends ResponseEntityExceptionHandler so Spring's own MVC errors (bad JSON, wrong method,
 * unknown route, type mismatch...) are covered too.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        // Generic on purpose: never reveal whether the email or the password was wrong.
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid email or password", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The request was modified by someone else. Reload and try again.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The operation conflicts with existing data", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }

    // ---- Spring MVC standard exceptions -------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDto(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing, malformed or contains an invalid value", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = status.is4xxClientError() ? status.getReasonPhrase() : "An unexpected error occurred";
        return respond(status, status.name(), message, List.of());
    }

    // ---- helpers ------------------------------------------------------------------------

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message,
                                                    List<FieldErrorDto> fieldErrors) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, code, message, fieldErrors));
    }

    private ResponseEntity<Object> respond(HttpStatus status, String code, String message,
                                           List<FieldErrorDto> fieldErrors) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, code, message, fieldErrors));
    }
}
