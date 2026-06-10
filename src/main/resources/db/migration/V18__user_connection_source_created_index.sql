CREATE INDEX idx_user_connection_source_created_id_desc
    ON user_connection (source_user_id, created_at DESC, id DESC);
