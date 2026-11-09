package com.cedrick.flowdeskbe.exception;

import com.cedrick.flowdeskbe.dto.FieldErrorDto;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Validation that depends on state (e.g. "reject needs a comment") and so cannot live on the DTO. */
public class BusinessValidationException extends ApiException {
    public BusinessValidationException(String message, String field) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, List.of(new FieldErrorDto(field, message)));
    }
}
