package com.cedrick.flowdeskbe.exception;

import org.springframework.http.HttpStatus;

/** The provider definitively refused the operation; nothing was processed. */
public class FulfillmentRejectedException extends ApiException {
    public FulfillmentRejectedException(String message) {
        super(HttpStatus.BAD_GATEWAY, "FULFILLMENT_REJECTED", message);
    }
}
