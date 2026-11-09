package com.cedrick.flowdeskbe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The one envelope every endpoint returns, success or failure:
 * {@code {timestamp, status, code, message, data, fieldErrors[]}}.
 * On errors {@code data} is omitted; on success {@code fieldErrors} is an empty list.
 */
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) T data,
        List<FieldErrorDto> fieldErrors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(Instant.now(), HttpStatus.OK.value(), "OK", "Success", data, List.of());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(Instant.now(), HttpStatus.OK.value(), "OK", message, data, List.of());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(Instant.now(), HttpStatus.CREATED.value(), "CREATED", "Created", data, List.of());
    }

    public static ApiResponse<Void> error(HttpStatus status, String code, String message) {
        return error(status, code, message, List.of());
    }

    public static ApiResponse<Void> error(HttpStatus status, String code, String message,
                                          List<FieldErrorDto> fieldErrors) {
        return new ApiResponse<>(Instant.now(), status.value(), code, message, null, fieldErrors);
    }
}
