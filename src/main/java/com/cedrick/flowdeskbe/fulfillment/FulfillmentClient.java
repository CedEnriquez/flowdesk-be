package com.cedrick.flowdeskbe.fulfillment;

import java.util.Optional;

/**
 * Port to the external fulfillment provider. Implementations MUST pass the idempotency key to the
 * provider so that repeating {@link #fulfill} with the same key never performs the work twice.
 */
public interface FulfillmentClient {

    /**
     * Performs the operation (or returns the result of the earlier identical call).
     *
     * @throws FulfillmentTimeoutException if no usable response arrived: the operation MAY have happened
     * @throws com.cedrick.flowdeskbe.exception.FulfillmentRejectedException if the provider definitively refused it
     */
    FulfillmentResult fulfill(String idempotencyKey, long requestId);

    /** Asks the provider what happened to an earlier call. Empty means it was never processed. */
    Optional<FulfillmentResult> lookup(String idempotencyKey);
}
