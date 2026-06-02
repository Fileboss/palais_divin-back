ALTER TABLE restaurant ADD COLUMN avg_rating numeric(3,2);

UPDATE restaurant r
SET avg_rating = (SELECT AVG(rating)::numeric(3,2)
                  FROM review rev
                  WHERE rev.restaurant_id = r.id);

CREATE OR REPLACE FUNCTION refresh_restaurant_avg_rating()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  target_id uuid;
BEGIN
  target_id := COALESCE(NEW.restaurant_id, OLD.restaurant_id);
  UPDATE restaurant
  SET avg_rating = (SELECT AVG(rating) FROM review WHERE restaurant_id = target_id)
  WHERE id = target_id;
  RETURN NULL;
END;
$$;

CREATE TRIGGER trg_restaurant_avg_rating
AFTER INSERT OR UPDATE OF rating OR DELETE ON review
FOR EACH ROW EXECUTE FUNCTION refresh_restaurant_avg_rating();
