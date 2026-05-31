CREATE TABLE idempotency_key (
    id             uuid         PRIMARY KEY,
    key            varchar(255) NOT NULL,
    user_id        uuid         NOT NULL REFERENCES app_user(id),
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   uuid         NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key_user UNIQUE (key, user_id)
);

CREATE INDEX idx_idempotency_key_created_at ON idempotency_key(created_at);
