-- users ---------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('REQUESTER', 'ADMIN'))
);

-- requests ------------------------------------------------------------
CREATE TABLE requests (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    description TEXT         NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    priority    VARCHAR(10)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_requests_title       CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_requests_description CHECK (length(trim(description)) > 0),
    CONSTRAINT chk_requests_type        CHECK (type IN ('GENERAL', 'SERVICE', 'BOOKING')),
    CONSTRAINT chk_requests_priority    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_requests_status      CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'FULFILLED', 'CANCELLED'))
);

CREATE INDEX idx_requests_owner_created  ON requests (owner_id, created_at DESC);
CREATE INDEX idx_requests_status_created ON requests (status, created_at DESC);

-- request_activity (append-only audit trail) --------------------------
CREATE TABLE request_activity (
    id              BIGSERIAL   PRIMARY KEY,
    request_id      BIGINT      NOT NULL REFERENCES requests (id),
    previous_status VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    actor_id        BIGINT      NOT NULL REFERENCES users (id),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_activity_prev_status CHECK (previous_status IS NULL
        OR previous_status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT chk_activity_new_status  CHECK (new_status
        IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'FULFILLED', 'CANCELLED')),
    -- A NULL comment would make "length(trim(comment)) > 0" evaluate to NULL, which a CHECK
    -- treats as passing. The explicit IS NOT NULL makes the rejection reason truly mandatory.
    CONSTRAINT chk_activity_reject_reason CHECK (new_status <> 'REJECTED'
        OR (comment IS NOT NULL AND length(trim(comment)) > 0))
);

CREATE INDEX idx_activity_request_history ON request_activity (request_id, created_at, id);

-- Immutability: audit rows can never be changed or removed, even by a buggy query.
CREATE FUNCTION forbid_activity_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_activity is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_no_update_delete
    BEFORE UPDATE OR DELETE ON request_activity
    FOR EACH ROW EXECUTE FUNCTION forbid_activity_modification();

CREATE TRIGGER trg_activity_no_truncate
    BEFORE TRUNCATE ON request_activity
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_activity_modification();
