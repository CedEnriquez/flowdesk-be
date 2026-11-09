package com.cedrick.flowdeskbe.entity;

import com.cedrick.flowdeskbe.enums.ExternalOperationState;
import com.cedrick.flowdeskbe.enums.ExternalOperationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ledger of calls to an external provider. The {@code idempotencyKey} is stable per
 * (request, operation) and is sent to the provider, so every retry of the same operation
 * is recognised there and executed at most once.
 */
@Entity
@Table(name = "external_operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalOperation {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30, updatable = false)
    private ExternalOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalOperationState state;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ExternalOperation pending(Long requestId, ExternalOperationType type) {
        ExternalOperation op = new ExternalOperation();
        op.requestId = requestId;
        op.operationType = type;
        op.idempotencyKey = type.name().toLowerCase() + "-request-" + requestId;
        op.state = ExternalOperationState.PENDING;
        op.createdAt = Instant.now();
        op.updatedAt = op.createdAt;
        return op;
    }

    public boolean isSucceeded() {
        return state == ExternalOperationState.SUCCEEDED;
    }

    public void recordAttempt() {
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public void recordFailure(String message) {
        this.lastError = message == null ? null : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        this.updatedAt = Instant.now();
    }

    public void markSucceeded(String externalReference) {
        this.state = ExternalOperationState.SUCCEEDED;
        this.externalReference = externalReference;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }
}
