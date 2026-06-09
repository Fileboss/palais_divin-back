-- Pre-launch: wipe tag rows so the new taxonomy is the only source of truth.
TRUNCATE restaurant_tag, tag CASCADE;

ALTER TABLE tag DROP CONSTRAINT IF EXISTS tag_category_check;
ALTER TABLE tag
    ADD CONSTRAINT tag_category_check
    CHECK (category IN ('REGIME','TYPE','SPECIALTY','VENUE_TYPE','SERVICE_AND_PLACE'));

CREATE TABLE tag_implication (
    tag_id         uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    implies_tag_id uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_tag_implication PRIMARY KEY (tag_id, implies_tag_id),
    CONSTRAINT tag_implication_not_self CHECK (tag_id <> implies_tag_id)
);

CREATE INDEX idx_tag_implication_implies ON tag_implication(implies_tag_id);
