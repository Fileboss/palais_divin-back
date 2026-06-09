ALTER TABLE restaurant_tag DROP CONSTRAINT restaurant_tag_tag_id_fkey;

ALTER TABLE restaurant_tag
  ADD CONSTRAINT restaurant_tag_tag_id_fkey
  FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE;
