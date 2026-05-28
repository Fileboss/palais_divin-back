CREATE TABLE outbox_event (
    id             uuid        PRIMARY KEY,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     text        NOT NULL,
    payload        jsonb       NOT NULL,
    status         text        NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count    int         NOT NULL DEFAULT 0,
    last_error     text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    processed_at   timestamptz
);

CREATE INDEX idx_outbox_event_status_created_at ON outbox_event (status, created_at);
