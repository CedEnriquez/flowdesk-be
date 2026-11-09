package com.cedrick.flowdeskbe.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ApiException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
