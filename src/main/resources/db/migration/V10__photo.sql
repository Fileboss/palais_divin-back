CREATE TABLE photo (
    id            uuid         PRIMARY KEY,
    restaurant_id uuid         NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    author_id     uuid         NOT NULL REFERENCES app_user(id),
    object_key    text         NOT NULL,
    content_type  varchar(127) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_photo_object_key UNIQUE (object_key)
);

CREATE INDEX idx_photo_restaurant_created ON photo(restaurant_id, created_at DESC, id);
