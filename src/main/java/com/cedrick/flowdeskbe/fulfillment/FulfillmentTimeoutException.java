package com.cedrick.flowdeskbe.fulfillment;

/** The call timed out or the connection dropped: the outcome at the provider is UNKNOWN. */
public class FulfillmentTimeoutException extends RuntimeException {
    public FulfillmentTimeoutException(String message) {
        super(message);
    }
}
