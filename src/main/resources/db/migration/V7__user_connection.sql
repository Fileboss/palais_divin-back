CREATE TABLE user_connection (
    id UUID PRIMARY KEY,
    source_user_id UUID NOT NULL REFERENCES app_user(id),
    target_user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_connection_source_target UNIQUE (source_user_id, target_user_id),
    CONSTRAINT chk_user_connection_no_self CHECK (source_user_id <> target_user_id)
);
