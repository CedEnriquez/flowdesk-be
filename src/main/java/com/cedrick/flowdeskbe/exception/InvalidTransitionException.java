package com.cedrick.flowdeskbe.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransitionException extends ApiException {
    public InvalidTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_TRANSITION", message);
    }
}
