CREATE TABLE restaurant_tag (
    restaurant_id uuid        NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    tag_id        uuid        NOT NULL REFERENCES tag(id),
    attached_by   uuid        NOT NULL REFERENCES app_user(id),
    attached_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_restaurant_tag PRIMARY KEY (restaurant_id, tag_id)
);

CREATE INDEX idx_restaurant_tag_tag ON restaurant_tag(tag_id);
