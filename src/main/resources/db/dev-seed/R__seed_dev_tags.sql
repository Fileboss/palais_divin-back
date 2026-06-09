-- Dev-only seed for the 5-category tag catalogue + a handful of implication links.
-- Idempotent via ON CONFLICT DO NOTHING so re-runs are safe.

INSERT INTO tag (id, category, slug, label, created_at) VALUES
    -- REGIME
    ('a0000000-0000-0000-0000-000000000001', 'REGIME',  'vegan-100',         '100% vegan',       now()),
    ('a0000000-0000-0000-0000-000000000002', 'REGIME',  'vegan-option',      'Option vegan',     now()),
    ('a0000000-0000-0000-0000-000000000003', 'REGIME',  'vegetarien',        'Végétarien',       now()),
    ('a0000000-0000-0000-0000-000000000004', 'REGIME',  'sans-gluten-strict','Sans gluten strict', now()),
    ('a0000000-0000-0000-0000-000000000005', 'REGIME',  'sans-gluten-option','Option sans gluten', now()),
    ('a0000000-0000-0000-0000-000000000006', 'REGIME',  'halal',             'Halal',            now()),
    -- TYPE (nationality / culinary tradition)
    ('a0000000-0000-0000-0000-000000000010', 'TYPE',    'francais',          'Français',         now()),
    ('a0000000-0000-0000-0000-000000000011', 'TYPE',    'italien',           'Italien',          now()),
    ('a0000000-0000-0000-0000-000000000012', 'TYPE',    'japonais',          'Japonais',         now()),
    ('a0000000-0000-0000-0000-000000000013', 'TYPE',    'libanais',          'Libanais',         now()),
    ('a0000000-0000-0000-0000-000000000014', 'TYPE',    'mexicain',          'Mexicain',         now()),
    -- SPECIALTY
    ('a0000000-0000-0000-0000-000000000020', 'SPECIALTY','sushi',            'Sushi',            now()),
    ('a0000000-0000-0000-0000-000000000021', 'SPECIALTY','pizza',            'Pizza',            now()),
    ('a0000000-0000-0000-0000-000000000022', 'SPECIALTY','burger',           'Burger',           now()),
    ('a0000000-0000-0000-0000-000000000023', 'SPECIALTY','ramen',            'Ramen',            now()),
    ('a0000000-0000-0000-0000-000000000024', 'SPECIALTY','tapas',            'Tapas',            now()),
    -- VENUE_TYPE
    ('a0000000-0000-0000-0000-000000000030', 'VENUE_TYPE','gastronomique',   'Gastronomique',    now()),
    ('a0000000-0000-0000-0000-000000000031', 'VENUE_TYPE','bistrot',         'Bistrot',          now()),
    ('a0000000-0000-0000-0000-000000000032', 'VENUE_TYPE','fast-food',       'Fast-food',        now()),
    ('a0000000-0000-0000-0000-000000000033', 'VENUE_TYPE','brasserie',       'Brasserie',        now()),
    -- SERVICE_AND_PLACE
    ('a0000000-0000-0000-0000-000000000040', 'SERVICE_AND_PLACE','terrasse',     'Terrasse',     now()),
    ('a0000000-0000-0000-0000-000000000041', 'SERVICE_AND_PLACE','rooftop',      'Rooftop',      now()),
    ('a0000000-0000-0000-0000-000000000042', 'SERVICE_AND_PLACE','salle-privee', 'Salle privée', now()),
    ('a0000000-0000-0000-0000-000000000043', 'SERVICE_AND_PLACE','pmr',          'PMR',          now()),
    ('a0000000-0000-0000-0000-000000000044', 'SERVICE_AND_PLACE','kids-friendly','Kids-friendly',now()),
    ('a0000000-0000-0000-0000-000000000045', 'SERVICE_AND_PLACE','wifi',         'Wi-Fi',        now())
ON CONFLICT DO NOTHING;

-- Implications: filtering by the right column also matches restaurants tagged with the left column.
INSERT INTO tag_implication (tag_id, implies_tag_id, created_at) VALUES
    -- vegan-100 -> vegan-option (a strict vegan restaurant satisfies a vegan-option search)
    ('a0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', now()),
    -- vegetarien -> vegan-option (a vegetarian restaurant satisfies a vegan-option search)
    ('a0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000002', now()),
    -- sans-gluten-strict -> sans-gluten-option
    ('a0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000005', now())
ON CONFLICT DO NOTHING;
