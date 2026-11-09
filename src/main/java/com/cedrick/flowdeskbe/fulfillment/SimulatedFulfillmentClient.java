package com.cedrick.flowdeskbe.fulfillment;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the real provider so the flow works end-to-end locally. It behaves like a well-built
 * provider: the first call for a key does the work, repeats with the same key return the SAME result.
 * Replace it with a real HTTP client (declared as the {@code FulfillmentClient} bean) when one exists.
 */
@Component
public class SimulatedFulfillmentClient implements FulfillmentClient {

    private final ConcurrentHashMap<String, FulfillmentResult> processed = new ConcurrentHashMap<>();

    @Override
    public FulfillmentResult fulfill(String idempotencyKey, long requestId) {
        return processed.computeIfAbsent(idempotencyKey, key -> new FulfillmentResult("SIM-" + UUID.randomUUID()));
    }

    @Override
    public Optional<FulfillmentResult> lookup(String idempotencyKey) {
        return Optional.ofNullable(processed.get(idempotencyKey));
    }
}
