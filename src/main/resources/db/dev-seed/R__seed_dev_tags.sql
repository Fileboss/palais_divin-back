-- Dev-only seed for the 5-category tag catalogue + a handful of implication links.
-- Canonical `label` is French (I9.1 fallback convention); `label_i18n` carries the FE locales (en/es/de/zh/ko/ja).
-- Re-running this seed (on checksum change) backfills `label_i18n` on rows previously inserted without it.

INSERT INTO tag (id, category, slug, label, label_i18n, created_at) VALUES
    -- REGIME
    ('a0000000-0000-0000-0000-000000000001', 'REGIME',  'vegan-100',         '100% vegan',
     '{"en":"100% vegan","es":"100% vegano","de":"100% vegan","zh":"全素","ko":"100% 비건","ja":"100%ヴィーガン"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000002', 'REGIME',  'vegan-option',      'Option vegan',
     '{"en":"Vegan option","es":"Opción vegana","de":"Vegane Option","zh":"纯素选项","ko":"비건 옵션","ja":"ヴィーガン対応"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000003', 'REGIME',  'vegetarien',        'Végétarien',
     '{"en":"Vegetarian","es":"Vegetariano","de":"Vegetarisch","zh":"素食","ko":"채식","ja":"ベジタリアン"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000004', 'REGIME',  'sans-gluten-strict','Sans gluten strict',
     '{"en":"Gluten-free (strict)","es":"Sin gluten estricto","de":"Streng glutenfrei","zh":"严格无麸质","ko":"글루텐 프리 (엄격)","ja":"完全グルテンフリー"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000005', 'REGIME',  'sans-gluten-option','Option sans gluten',
     '{"en":"Gluten-free option","es":"Opción sin gluten","de":"Glutenfreie Option","zh":"无麸质选项","ko":"글루텐 프리 옵션","ja":"グルテンフリー対応"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000006', 'REGIME',  'halal',             'Halal',
     '{"en":"Halal","es":"Halal","de":"Halal","zh":"清真","ko":"할랄","ja":"ハラール"}'::jsonb, now()),
    -- TYPE (nationality / culinary tradition)
    ('a0000000-0000-0000-0000-000000000010', 'TYPE',    'francais',          'Français',
     '{"en":"French","es":"Francesa","de":"Französisch","zh":"法国菜","ko":"프랑스 요리","ja":"フランス料理"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000011', 'TYPE',    'italien',           'Italien',
     '{"en":"Italian","es":"Italiana","de":"Italienisch","zh":"意大利菜","ko":"이탈리아 요리","ja":"イタリア料理"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000012', 'TYPE',    'japonais',          'Japonais',
     '{"en":"Japanese","es":"Japonesa","de":"Japanisch","zh":"日本料理","ko":"일본 요리","ja":"日本料理"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000013', 'TYPE',    'libanais',          'Libanais',
     '{"en":"Lebanese","es":"Libanesa","de":"Libanesisch","zh":"黎巴嫩菜","ko":"레바논 요리","ja":"レバノン料理"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000014', 'TYPE',    'mexicain',          'Mexicain',
     '{"en":"Mexican","es":"Mexicana","de":"Mexikanisch","zh":"墨西哥菜","ko":"멕시코 요리","ja":"メキシコ料理"}'::jsonb, now()),
    -- SPECIALTY
    ('a0000000-0000-0000-0000-000000000020', 'SPECIALTY','sushi',            'Sushi',
     '{"en":"Sushi","es":"Sushi","de":"Sushi","zh":"寿司","ko":"스시","ja":"寿司"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000021', 'SPECIALTY','pizza',            'Pizza',
     '{"en":"Pizza","es":"Pizza","de":"Pizza","zh":"披萨","ko":"피자","ja":"ピザ"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000022', 'SPECIALTY','burger',           'Burger',
     '{"en":"Burger","es":"Hamburguesa","de":"Burger","zh":"汉堡","ko":"버거","ja":"ハンバーガー"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000023', 'SPECIALTY','ramen',            'Ramen',
     '{"en":"Ramen","es":"Ramen","de":"Ramen","zh":"拉面","ko":"라멘","ja":"ラーメン"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000024', 'SPECIALTY','tapas',            'Tapas',
     '{"en":"Tapas","es":"Tapas","de":"Tapas","zh":"塔帕斯","ko":"타파스","ja":"タパス"}'::jsonb, now()),
    -- VENUE_TYPE
    ('a0000000-0000-0000-0000-000000000030', 'VENUE_TYPE','gastronomique',   'Gastronomique',
     '{"en":"Fine dining","es":"Gastronómico","de":"Gourmetrestaurant","zh":"高级餐厅","ko":"파인 다이닝","ja":"ガストロノミー"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000031', 'VENUE_TYPE','bistrot',         'Bistrot',
     '{"en":"Bistro","es":"Bistró","de":"Bistro","zh":"小酒馆","ko":"비스트로","ja":"ビストロ"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000032', 'VENUE_TYPE','fast-food',       'Fast-food',
     '{"en":"Fast food","es":"Comida rápida","de":"Fast Food","zh":"快餐","ko":"패스트푸드","ja":"ファストフード"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000033', 'VENUE_TYPE','brasserie',       'Brasserie',
     '{"en":"Brasserie","es":"Brasserie","de":"Brasserie","zh":"啤酒餐厅","ko":"브라스리","ja":"ブラッスリー"}'::jsonb, now()),
    -- SERVICE_AND_PLACE
    ('a0000000-0000-0000-0000-000000000040', 'SERVICE_AND_PLACE','terrasse',     'Terrasse',
     '{"en":"Terrace","es":"Terraza","de":"Terrasse","zh":"露台","ko":"테라스","ja":"テラス"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000041', 'SERVICE_AND_PLACE','rooftop',      'Rooftop',
     '{"en":"Rooftop","es":"Azotea","de":"Dachterrasse","zh":"屋顶","ko":"루프탑","ja":"ルーフトップ"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000042', 'SERVICE_AND_PLACE','salle-privee', 'Salle privée',
     '{"en":"Private room","es":"Sala privada","de":"Separee","zh":"包间","ko":"프라이빗 룸","ja":"個室"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000043', 'SERVICE_AND_PLACE','pmr',          'PMR',
     '{"en":"Wheelchair accessible","es":"Accesible PMR","de":"Barrierefrei","zh":"无障碍","ko":"장애인 접근 가능","ja":"バリアフリー"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000044', 'SERVICE_AND_PLACE','kids-friendly','Kids-friendly',
     '{"en":"Kid-friendly","es":"Apto para niños","de":"Kinderfreundlich","zh":"亲子友好","ko":"어린이 친화적","ja":"子連れ歓迎"}'::jsonb, now()),
    ('a0000000-0000-0000-0000-000000000045', 'SERVICE_AND_PLACE','wifi',         'Wi-Fi',
     '{"en":"Wi-Fi","es":"Wi-Fi","de":"WLAN","zh":"Wi-Fi","ko":"와이파이","ja":"Wi-Fi"}'::jsonb, now())
ON CONFLICT (id) DO UPDATE SET
    label      = EXCLUDED.label,
    label_i18n = EXCLUDED.label_i18n;

-- Implications: filtering by the right column also matches restaurants tagged with the left column.
INSERT INTO tag_implication (tag_id, implies_tag_id, created_at) VALUES
    -- vegan-100 -> vegan-option (a strict vegan restaurant satisfies a vegan-option search)
    ('a0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', now()),
    -- vegetarien -> vegan-option (a vegetarian restaurant satisfies a vegan-option search)
    ('a0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000002', now()),
    -- sans-gluten-strict -> sans-gluten-option
    ('a0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000005', now())
ON CONFLICT DO NOTHING;
