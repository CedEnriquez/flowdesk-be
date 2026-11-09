package com.cedrick.flowdeskbe.exception;

import org.springframework.http.HttpStatus;

/** The provider did not answer and could not confirm the result. Retrying is safe (same idempotency key). */
public class FulfillmentOutcomeUnknownException extends ApiException {
    public FulfillmentOutcomeUnknownException() {
        super(HttpStatus.GATEWAY_TIMEOUT, "FULFILLMENT_OUTCOME_UNKNOWN",
                "The fulfillment service did not confirm the result. The request was not changed. "
                        + "You can safely retry: it will not be processed twice.");
    }
}
