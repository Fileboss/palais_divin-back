CREATE INDEX idx_review_author_created_id_desc
  ON review (author_id, created_at DESC, id DESC);
