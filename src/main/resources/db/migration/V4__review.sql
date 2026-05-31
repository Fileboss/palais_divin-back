CREATE TABLE review (
    id            uuid        PRIMARY KEY,
    restaurant_id uuid        NOT NULL REFERENCES restaurant(id),
    author_id     uuid        NOT NULL REFERENCES app_user(id),
    rating        int         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_restaurant_author UNIQUE (restaurant_id, author_id)
);
