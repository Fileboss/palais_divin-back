-- Dev-only seed. Loaded only when application-dev.properties adds
-- classpath:db/migration-dev to spring.flyway.locations — prod profile
-- never sees this directory. UUIDs match the pinned `id` values for
-- testuser/testadmin in compose/keycloak/realm-palaisdivin.json so the
-- JWT `sub` claim resolves to an app_user row.
INSERT INTO app_user (id, subject, email, display_name, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111',
     '11111111-1111-1111-1111-111111111111',
     'testuser@palaisdivin.lepgu.fr',
     'Test User',
     now()),
    ('22222222-2222-2222-2222-222222222222',
     '22222222-2222-2222-2222-222222222222',
     'testadmin@palaisdivin.lepgu.fr',
     'Test Admin',
     now())
ON CONFLICT DO NOTHING;
