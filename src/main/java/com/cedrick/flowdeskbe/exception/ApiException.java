package com.cedrick.flowdeskbe.exception;

import com.cedrick.flowdeskbe.dto.FieldErrorDto;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Base for every business error; the global handler turns it into an ApiResponse. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorDto> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<FieldErrorDto> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }
}
