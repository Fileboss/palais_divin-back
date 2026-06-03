CREATE TABLE tag (
    id         uuid         PRIMARY KEY,
    category   varchar(32)  NOT NULL CHECK (category IN ('FOOD','REGIME','PLACE','VENUE_TYPE')),
    slug       varchar(64)  NOT NULL,
    label      varchar(127) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tag_slug UNIQUE (slug)
);

CREATE INDEX idx_tag_category_slug ON tag(category, slug);
