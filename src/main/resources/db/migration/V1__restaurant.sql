CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE restaurant (
    id         uuid                  PRIMARY KEY,
    name       text                  NOT NULL,
    address    text,
    location   geography(Point,4326) NOT NULL,
    created_at timestamptz           NOT NULL DEFAULT now()
);

CREATE INDEX idx_restaurant_location ON restaurant USING GIST (location);
