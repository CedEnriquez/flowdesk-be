-- external_operations: one row per call to an external provider made on behalf of a request.
-- The idempotency_key is sent to the provider, so retrying after a timeout can never run the
-- operation twice. A PENDING row means "attempted, outcome not yet confirmed locally".
CREATE TABLE external_operations (
    id                 BIGSERIAL    PRIMARY KEY,
    request_id         BIGINT       NOT NULL REFERENCES requests (id),
    operation_type     VARCHAR(30)  NOT NULL,
    idempotency_key    VARCHAR(100) NOT NULL UNIQUE,
    state              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    external_reference VARCHAR(100),
    attempts           INTEGER      NOT NULL DEFAULT 0,
    last_error         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_op_request_type UNIQUE (request_id, operation_type),
    CONSTRAINT chk_external_op_type  CHECK (operation_type IN ('FULFILL')),
    CONSTRAINT chk_external_op_state CHECK (state IN ('PENDING', 'SUCCEEDED')),
    CONSTRAINT chk_external_op_ref   CHECK (state <> 'SUCCEEDED' OR external_reference IS NOT NULL)
);
