ALTER TABLE review DROP CONSTRAINT review_restaurant_id_fkey;

ALTER TABLE review
    ADD CONSTRAINT review_restaurant_id_fkey
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE CASCADE;
