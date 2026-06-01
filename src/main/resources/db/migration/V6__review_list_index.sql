CREATE INDEX idx_review_restaurant_created_id_desc
  ON review (restaurant_id, created_at DESC, id DESC);
