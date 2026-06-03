# ROADMAP — Le Palais Divin (backend)

Iterative, "walking-skeleton-first" plan. Each task is **one Claude session**: clear scope, clear "done when", verifiable. Milestones (`MILESTONE` markers) gate phase transitions.

Conventions used here:
- **Path-rooted** task names so Claude can locate the work without re-discovery.
- **Done when** is the only acceptance criterion — if it passes, the task ships.
- **Out of scope** lines exist to prevent silent feature creep inside a task.
- Defer to `README.md` for spec details; this file decides *order*, not *what*.

Status legend: `[ ]` todo · `[x]` done · `[~]` in progress · `[?]` blocked / needs decision.

---

## Phase M0 — Scaffolding sanity

Goal: the repo boots, compiles, and the basic dev loop works. No business code yet.

- [x] **M0.1 — Initializr scaffold + manual deps** — `pom.xml`, `compose.yaml`, wrapper, package layout. _(commit 2cb600d)_
- [x] **M0.2 — Add `.gitignore`** — Maven/IntelliJ/macOS. Stop `target/`, `.idea/`, `.DS_Store` polluting `git status`.
- [x] **M0.3 — Empty ArchUnit rules class** — `src/test/java/.../architecture/ArchitectureRulesTest.java` with one passing rule (e.g. "no class in `domain/**` imports `org.springframework.*`"). README §8.2.
- [x] **M0.4 — Spotless plugin** — wire `com.diffplug.spotless:spotless-maven-plugin` with Google Java Format + import order. README §11.
- [x] **M0.5 — README: local dev prerequisites note** — Docker Desktop / OrbStack / Colima must be running before `mvn verify`. Symptom of missing it: `Could not find a valid Docker environment`.
- [x] **M0.6 — Local datasource bootstrap for `spring-boot:run`** — add `org.springframework.boot.service-connection` labels to `postgres`/`neo4j`/`minio` in `compose.yaml` so Boot's docker-compose support auto-injects connection details (the `postgis/postgis` image isn't in Boot's default-recognized list).

`MILESTONE M0` — `mvn clean verify -P integration-tests` runs green on a fresh clone (Docker daemon up). No surprises in `git status`.

---

## Phase M1 — First end-to-end vertical (anonymous, no DB)

Goal: an HTTP request hits the running app and gets a well-formed response. Walking skeleton — wire, not feature.

- [x] **M1.1 — Public health endpoint** — `GET /api/v1/public/ping` returns `{"status":"ok","ts":<iso>}` via a controller in `shared/adapters/web/`.
- [x] **M1.2 — Global `ProblemDetail` advice** — `@RestControllerAdvice` in `shared/adapters/web/` returning `application/problem+json` per RFC 9457. Handles `MethodArgumentNotValidException`, `NoResourceFoundException`, fallback `Exception`. README §6.
- [x] **M1.3 — Actuator exposure baseline** — expose `health`, `info`, `metrics`, `prometheus` under `/actuator/**`; lock everything else.
- [x] **M1.4 — Path-prefix `SecurityFilterChain` skeleton** — public/user/admin chain stubs. README §7.1.

`MILESTONE M1` — App boots, public endpoint returns 200, errors are ProblemDetail, security skeleton is in place. **Frontend can stand up** against a stable contract for public endpoints.

---

## Phase M2 — First persisted aggregate: `Restaurant` on Postgres

Goal: write + read one aggregate end to end. Postgres only, no Neo4j yet, no auth yet.

- [x] **M2.1 — Flyway baseline + V1 migration** — `db/migration/V1__restaurant.sql`: enable `postgis`, create `restaurant(id, name, address, location geography(Point,4326), created_at)`, GIST index on `location`.
- [x] **M2.2 — `restaurant/domain` skeleton** — `Restaurant` record, `RestaurantId` VO, `Coordinates` VO, `RestaurantRepositoryPort` in `domain/ports/`. JDK-only imports; ArchUnit rule asserts it.
- [x] **M2.3 — `restaurant/application/RestaurantService`** — `create(...)`, `findById(...)`. Implements `CreateRestaurantUseCase` / `FindRestaurantUseCase` defined in `domain/ports/`.
- [x] **M2.4 — `restaurant/adapters/postgres`** — JPA entity + repository implementing the domain port. Mapping `Coordinates` ↔ `geography(Point,4326)`. Wires `@Service` on `RestaurantService` + a `Clock` bean (both deferred from M2.3 — couldn't ship before the port had an adapter, would break `contextLoads`).
- [x] **M2.5 — `restaurant/adapters/rest`** — `POST /api/v1/public/restaurants` + `GET /api/v1/public/restaurants/{id}`. DTOs as records.
- [x] **M2.6 — Keyset pagination on list endpoint** — `GET /api/v1/public/restaurants?cursor=&size=&sort=`. `Slice<T>` (no `COUNT(*)`). Cursor = Base64URL of `{k, id, v}`. Envelope `{ data, page: { size, hasNext, nextCursor } }`. README §7.4.
- [x] **M2.7 — OpenAPI spec generated at build time** — wire springdoc-openapi so `mvn verify` writes a committed `docs/openapi.yaml`; CI fails if stale relative to the code.
- [x] **M2.8 — Server-side address geocoding (Base Adresse Nationale)** — `POST` now takes `{ name, address }`; resolved server-side via BAN with an `@HttpExchange` client (2s connect+read timeout). Caffeine cache (24h TTL, normalized key). Unresolvable address → 422 ProblemDetail. Added post-M2 — frontend needed it before building the create form (same retroactive-fix pattern as M0.6).

`MILESTONE M2` — One aggregate persisted, queryable, paginated, submitted by address (server-geocoded), with a published OpenAPI contract. Frontend can render a list and a create form.

---

## Phase M3 — Authentication via Keycloak

Goal: real auth. Public stays public; user-scoped endpoints require a valid JWT.

- [x] **M3.1 — `config/security` — OAuth2 Resource Server, STATELESS** — `application.yml` issuer-uri, JWT decoder, role mapping (`realm_access.roles` → `ROLE_*`). README §7.1.
- [x] **M3.2 — `compose.yaml` realm bootstrap** — import a `palaisdivin` realm JSON with one test client + one test user at container start. Kept in `compose/keycloak/realm-palaisdivin.json`.
- [x] **M3.3 — Move `Restaurant` GETs under `/api/v1/user/restaurants`** — keep `/public` only for ping and obvious anon endpoints. _(Partially reverted by I2 — see below.)_
- [x] **M3.4 — Integration test with `testcontainers-keycloak`** — boot Keycloak in IT, mint a token, hit `/user/restaurants`.

`MILESTONE M3` — End-to-end **secure** vertical. **Frontend integrates the Keycloak login flow now.** First "demo-able" state.

---

## Phase M4 — Transactional Outbox + Neo4j projection

Goal: dual-store sync that respects the README's eventual-consistency model.

- [x] **M4.1 — `outbox_event` table** — V2 migration: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload jsonb`, `status`, `created_at`, `processed_at`; index on `(status, created_at)`. README §5.3.
- [x] **M4.2 — `shared/adapters/outbox/OutboxPublisher`** — domain-facing port + JPA-backed adapter. Writes payload in the same tx as the aggregate.
- [x] **M4.3 — `shared/adapters/outbox/OutboxWorker`** — `@Scheduled`, `SELECT ... FOR UPDATE SKIP LOCKED LIMIT N`, dispatches to a `Projector` per `aggregate_type`, marks `PROCESSED`. Concurrent IT with 2 workers shows no double-processing.
- [x] **M4.4 — `restaurant/adapters/neo4j/RestaurantProjector`** — Cypher `MERGE (:Restaurant {id})` with name/coords. Idempotent. IT polls Neo4j with Awaitility.

`MILESTONE M4` — Dual store is wired and provably idempotent. From here on, anything cross-component goes through the outbox by default.

---

## Phase M5 — User + Invitation

Goal: invitation-only signup via Keycloak Admin API. No self-service.

- [x] **M5.1 — `user/domain`** — `User` aggregate (`id`, `subject`, `email`, `displayName`, `createdAt`), `InvitationToken` VO (opaque), `UserRepositoryPort` (`save`, `findById`, `findBySubject`), `UserNotFoundException`. Pure JDK; ArchUnit's `domainStaysFrameworkFree` / `domainOnlyDependsOnJdkAndDomain` extended to `user/domain/**`. `Invitation` aggregate + TTL/single-use behavior deferred (no caller yet).
- [x] **M5.2 — `user/adapters/postgres` + `Invitation` aggregate** — V3 migration: `app_user` (subject/email UNIQUE) + `invitation` (token UNIQUE, `expires_at`, `consumed_at`). `Invitation` lands as a pure data record; `isExpired`/`consume` deferred to M5.5 (no caller yet). `UserPostgresAdapter` + `InvitationPostgresAdapter` with static record↔entity mappers; `@DataJpaTest` ITs round-trip every field, exercise `findBySubject`/`findByToken`.
- [x] **M5.3 — `user/adapters/keycloak` — `@HttpExchange` client** — Spring 7 HTTP Interface clients (`KeycloakTokenClient`, `KeycloakAdminClient`) over `RestClient.Builder` with 2s timeouts; no `keycloak-admin-client` dep. `KeycloakAdminPort.createUser(...)` creates the user then assigns realm roles in two follow-up calls — Keycloak ignores `realmRoles` on user-create. `KeycloakTokenSupplier` caches the bearer with TTL = `expires_in − 30s`, refresh under `ReentrantLock` (virtual-thread friendly). Service-account client `palais-divin-backend` (with `realm-admin`) seeded in both realm JSONs.
- [x] **M5.4 — `POST /api/v1/admin/invitations`** — admin-only mint of a 48h-TTL `Invitation` via `Clock`. TTL + signup URL via `@Validated @ConfigurationProperties("app.invitation")`. Response `{id, expiresAt, signupUrl}` — token implicit in the URL. Deferred: `Invitation.{isExpired, consume}` + `issuedBy` audit (no caller yet).
- [x] **M5.5 — `POST /api/v1/public/signup`** — public consumer of M5.4 tokens. `SignupService @Transactional`: read invitation → validate via `Invitation.{isExpired, isConsumed, consume}` (this is M5.1/M5.2's deferred caller) → `KeycloakAdminPort.createUser(..., List.of("USER"))` → insert Postgres `User` + consume invitation + publish `UserCreated`, all in one Postgres tx. `UserProjector` MERGEs `(:User)` in Neo4j. Error mapping: unknown → 404; expired/consumed → 410 `/problems/invitation-not-usable`; Keycloak 409 → 409; other Keycloak → 502 `/problems/upstream-failure`; Postgres DIVE → 409 backstop. **Known limitation**: orphan Keycloak user on Postgres rollback, no compensation path. Deferred: `Invitation.issuedBy` audit.

`MILESTONE M5` — Onboarding flow is real. Frontend implements the invitation/signup screens.

---

## Intermediate phase I1 — Testing time optimization

- [x] **I1 — Single shared Spring IT context + singleton Testcontainers** — `TestcontainersConfiguration` containers become `private static final`, started via `Startables.deepStart(...)` at class load (Postgres + Neo4j `.withReuse(true)`; Keycloak unreusable due to `.withRealmImportFile(...)`). Every `*IT` extends `AbstractIntegrationTest`. `SharedTestStubs` exposes `BanApiClientStub`, `BlockingProjector`, `AlwaysFailingProjector` — test-side projectors must NOT collide with prod `aggregateType` (CLAUDE.md gotcha). `application-test.properties` sets `spring.task.scheduling.enabled=false` as the test default. Median IT-suite time 312s → 70s (-78%). — _MILESTONE I1_

---

## Intermediate phase I2 — Anonymous restaurant browsing

- [x] **I2 — Public restaurant read endpoints** — frontend wanted anonymous browsing; M3.3 had put all restaurant routes under `/user/`. New `PublicRestaurantRestController` exposes `GET /api/v1/public/restaurants` + `GET /api/v1/public/restaurants/{id}`, both reusing the existing use-case ports + DTOs (no per-user fields on the aggregate yet). Write path stays under `/user/`. **Establishes the codified CLAUDE.md rule "default catalog reads to `/public/**`".** — _MILESTONE I2_

---

## Phase M6 — Ratings

Goal: the core domain action.

- [x] **M6.1 — `review/domain`** — `Review` aggregate with `ReviewId` VO and cross-component refs (`RestaurantId`/`UserId`), `rating int` validated `[1,5]` inline (no `Score` VO — mirrors `User`'s inline-string idiom). Nullable `comment` rejected if blank to avoid `""` vs `null` ambiguity at persistence. `ReviewRepositoryPort` flat in `domain/ports/` with `save` + `findById` only (extra methods land with callers). README is authoritative — aggregate is `Review`, field is `rating` (ROADMAP's "Rating aggregate" wording was informal). Deferred: use-case interfaces (M6.3/M6.5), `events/ReviewCreated` (M6.4), Postgres adapter (M6.2), `tags` field.
- [x] **M6.2 — `review/adapters/postgres`** — V4 migration: `review` table with `CONSTRAINT uq_review_restaurant_author UNIQUE (restaurant_id, author_id)` — explicit name so M6.3 can pin the 409. UNIQUE is business rule per README §7.5. Composite list-query index deferred to M6.5 (sort/filter shape not yet fixed). FKs as raw `UUID` (no `@ManyToOne`) — DB enforces, domain carries the VOs. Adapter: `save` + `findById` only, no `@Transactional` (service owns the tx). DIVE propagated raw — M6.3 maps to 409. Note: `em.flush()` in `@DataJpaTest` bypasses Spring exception translation — assert on constraint *name*, not class (CLAUDE.md gotcha).
- [x] **M6.3 — `POST /api/v1/user/restaurants/{id}/reviews`** — `CreateReviewUseCase` + `ReviewService @Transactional` + `ReviewRestController`. Flow: resolve `jwt.subject → UserId`, idempotency lookup (hit → replay), restaurant exists check, save. Outbox publish deferred to M6.4 (ship with caller). **Cross-component precedent**: `ReviewService` imports `UserRepositoryPort` + `RestaurantRepositoryPort` directly — the "no cross-component imports" rule targets writes; sync read-only `domain/ports/*` imports are OK (cross-component `application/*` / `adapters/*` still forbidden). **Idempotency-Key infra** (V5 + generic `IdempotencyKeyPort`): table `(key, user_id, aggregate_type, aggregate_id, created_at)` with UNIQUE `(key, user_id)`. Generic port — `aggregateType` discriminates callers, don't fork per-aggregate. Concurrent same-key race → DIVE → 409.
- [x] **M6.4 — Outbox → Neo4j projector** — `(:User)-[:RATED {score}]->(:Restaurant)`. `events/ReviewCreated`. `ReviewService.create` publishes immediately after `reviews.save(...)`, **before** `idempotency.record(...)` — a publish failure rolls back the tx and leaves the idempotency key reusable. `ReviewProjector` MERGEs both endpoints + edge (MERGE not MATCH absorbs the race where `Review` drains before `User`/`Restaurant`). Bridge naming `payload.rating → edge.score` — M7.2 traversal uses `score`; API stays `rating`. No `comment` on edge — Postgres remains SoT for text. Deferred: `ReviewUpdated`/`ReviewDeleted`, `comment` on edge, `(:Review)` node, Neo4j unique constraints.
- [x] **M6.5 — `GET /api/v1/public/restaurants/{id}/reviews`** — keyset-paginated, under `/public/**` per the I2 precedent (catalog read, no per-user leak). New `ListReviewsUseCase.listByRestaurant`; `ReviewRepositoryPort` gains `findByRestaurant`. No restaurant-exists pre-check — unknown id returns empty page (404 is for GET-by-id). V6 adds composite index `idx_review_restaurant_created_id_desc` (M6.2's deferred index). **Cross-component refactor**: `InvalidCursorException` promoted from `restaurant/adapters/rest/` to `shared/adapters/web/` (second-caller move — HTTP-cursor-generic). `CursorCodec` stays duplicated as `ReviewCursorCodec` (generic-isation waits for a third caller). Deferred: `authorDisplayName` enrichment + `UserRepositoryPort.findByIds`, GET-by-id single review, `/user/**` friend-only variant (M7 dep), extra sorts/filters.

`MILESTONE M6` — A user can rate a restaurant, and the rating shows up in Neo4j. **First playable feature.**

---

## Phase M7 — Web of Trust (the actual product)

Goal: recommendations come from the graph, not from aggregates.

- [x] **M7.1 — `KNOWS` edge management** — `POST /api/v1/user/connections/{targetId}` creates a directed, unilateral `(:User)-[:KNOWS]->(:User)` edge. `ConnectionResult` carries a `created` flag for 201 vs 200 routing. **Idempotency via natural key** `(source, target)` — re-POST same target returns 200 + existing row, no outbox re-publish; the generic `Idempotency-Key` header is intentionally absent. `SelfConnectionException` → 422 `/problems/self-connection`; `UserNotFoundException` → 404 (promoted to `GlobalExceptionHandler`). V7: `user_connection` with UNIQUE `(source, target)` + CHECK `no_self`. `ConnectionProjector` MERGEs both User endpoints + KNOWS edge. Deferred: DELETE connection, GET /user/connections (list), bidirectional accept-handshake.
- [x] **M7.2 — `GET /api/v1/user/recommendations`** — Cypher friend-of-friend traversal `(:User)-[:KNOWS*1..2]->(:User)-[r:RATED]->(:Restaurant)`, weighted by `sum(r.score)`, keyset-paginated. **Read-only** — no migration, no outbox event, no projector; the graph already carries everything M7.1 + M6.4 + M4.4 projected. Cypher uses `WITH DISTINCT rest, rater, r.score` before `sum/count` to dedup the multi-path FoF case where a rater is reachable via several KNOWS chains. Self-exclusion: `rater.id <> $userId AND NOT (me)-[:RATED]->(rest)`. Order: `affinity DESC, rest.id ASC`. `RecommendationService` resolves `jwt.subject → UserId` via `UserRepositoryPort.findBySubject` — orphan KC sub → `IllegalStateException` (not `UserNotFoundException`, which is reserved for *target* users in connection flows). `RecommendationCursor(double affinity, RestaurantId id)` — typed double key, not the Instant of M2.6/M6.5 codecs. **Third-caller refactor**: `PageMeta` promoted to `shared/adapters/web/` (M6.5 explicitly deferred this until the third caller). `CursorCodec<T>` generic-isation **not** done — cursor shapes differ (Instant vs. double); promotion waits for a fourth caller. **Caveat**: ranking is eventually-consistent — a new `RATED` edge between fetches can shuffle order; ties stable by `id`, rank itself best-effort. Deferred: hop-decay weighting, generic `CursorCodec<T>`, `GET /user/restaurants/{id}/affinity` (M7.3).
- [x] **M7.3 — `GET /api/v1/user/restaurants/{id}/affinity`** — single-restaurant friend-of-friend score. Same Cypher shape as M7.2 but filtered to one restaurant and **without** `NOT (me)-[:RATED]->(rest)` — affinity is a reflective query, so the user's own rating doesn't invalidate the friend-network score. `RecommendationGraphPort.findAffinityFor` returns a non-`Optional` `RestaurantAffinity` — Cypher aggregations on empty input still produce a zero-row, so empty-friend-network surfaces as `affinity=0, recommenderCount=0` naturally. Service does Postgres existence check first → `RestaurantNotFoundException` → 404 already wired. Lean response `{ restaurantId, affinity, recommenderCount }` — name/address/coords already on the caller. No migration, no outbox event, no projector. — _MILESTONE M7_

---

## ~~Intermediate phase I3 — more endpoints~~ ✓ Done

`GET /api/v1/public/restaurants/{restaurantId}/reviews/author/{authorId}` — fetch a specific review by restaurant + author. `PUT /api/v1/user/restaurants/{restaurantId}/reviews` — replace a user's own review (idempotent by restaurant+author pair). `avg_rating` added to restaurant responses: stored on the `restaurant` table as `numeric(3,2)`, maintained by a Postgres trigger `trg_restaurant_avg_rating` that fires after any INSERT/UPDATE/DELETE on `review`. DB trigger chosen over a projector to keep the cross-component write contained at the DB layer. `ReviewUpdated` event published on PUT so Neo4j `RATED` edge score stays in sync. `ReviewNotFoundException` second constructor (restaurant+author path) added to `GlobalExceptionHandler`. All four features tested: unit (ReviewServiceTest +4, ReviewRestControllerTest +3, PublicReviewRestControllerTest +2), adapter (ReviewPostgresAdapterIT +3 incl. trigger assertion), and IT (ReviewRestIT +2, PublicReviewRestIT +2).

---

## Intermediate phase I5 — Admin restaurant deletion

- [x] **I5 — `DELETE /api/v1/admin/restaurants/{id}`** — admin-only hard delete returning 204. V9 alters `review.restaurant_id` FK to `ON DELETE CASCADE` so dependent reviews vanish with the restaurant; `RestaurantService.delete` does an existence check (404 via existing `RestaurantNotFoundException`) and publishes a `RestaurantDeleted` outbox event. `RestaurantProjector` switches on `eventType` — `RestaurantCreated` keeps its MERGE, `RestaurantDeleted` runs `MATCH (r:Restaurant {id}) DETACH DELETE r`, which sweeps incident `RATED` edges in one shot (no per-review `ReviewDeleted` event needed at the graph layer). Trigger `trg_restaurant_avg_rating` fires on each cascaded review delete with the soon-to-be-deleted restaurant id — wasted but correct (Postgres processes child cascade + AFTER triggers before parent DELETE). No `Idempotency-Key` (CLAUDE.md: user-endpoint mutations only). Soft delete remains in post-launch backlog.

---

## Phase M8 — Photos via MinIO presigned URLs

Goal: media without proxying bytes through Java. README §5.4.

- [x] **M8.1 — `config/MinioConfig`** — `MinioClient` bean built from `@Validated MinioProperties("app.minio")`; `OkHttpClient` wired into `MinioClient.builder().httpClient(...)` with 2s connect/read/write timeouts. Self-contained `@Configuration` — MinIO isn't an `@HttpExchange` client, doesn't belong in `HttpClientsConfig`. Stub `app.minio.{access-key,secret-key}=test` added to `application-test.properties` so `@NotBlank` doesn't break IT context-load. Deferred: `app.minio.bucket`, MinIO Testcontainer + `DynamicPropertyRegistrar`, presigned-URL methods — all ship with M8.2.
- [x] **M8.2 — `POST /api/v1/user/restaurants/{restaurantId}/photos/upload-url`** — non-mutating: signs a PUT URL, no Postgres write, no outbox, no `Idempotency-Key`. New `photo/` vertical slice (`PhotoUploadUrl` model, `MintPhotoUploadUrlUseCase` + `PhotoStoragePort`, `PhotoService` `@Transactional(readOnly = true)`, `PhotoMinioAdapter`, `PhotoUploadUrlRestController`). Restaurant existence check via cross-component `RestaurantRepositoryPort` (M6.3 precedent). Object key: `restaurants/{restaurantId}/{uuid}` — natural prefix, no file extension (M8.3 will store `contentType` server-side). Response: `{ objectKey, uploadUrl, expiresAt }` — 200 OK (no resource created server-side). TTL = `app.minio.upload-url-ttl=10m`, bucket = `app.minio.bucket=palaisdivin-photos` (both new fields on `MinioProperties`). `MinioClient.getPresignedObjectUrl(Method.PUT, ...)`; checked `MinioException | GeneralSecurityException | IOException` wrapped in `PhotoStorageException` → 502 `upstream-failure` (mirrors `KeycloakOperationException`). `MinIOContainer` joined `Startables.deepStart`; bucket seeded once in `TestcontainersConfiguration`'s static block via a throwaway `MinioClient` (guarantees existence before any Spring bean wires up); endpoint/credentials registered via `DynamicPropertyRegistrar`. IT uploads real bytes with `java.net.http.HttpClient` (Spring `RestClient.put(...).contentType(...)` against a presigned URL trips MinIO's `MissingFields` — the signature only covers the URL+verb, extra headers are surplus). Deferred: `app.minio.bucket` auto-create in dev/prod (operator step), content-type whitelist, photo registration (M8.3), download URL (M8.4).
- [x] **M8.3 — `POST /api/v1/user/restaurants/{restaurantId}/photos`** — registers an uploaded object key + metadata. New `Photo` aggregate + `photo` table (V10, FK to restaurant `ON DELETE CASCADE`, `uq_photo_object_key` UNIQUE, `idx_photo_restaurant_created` for future list). `PhotoService` switches to class-level `@Transactional` with `mint(...)` annotated `@Transactional(readOnly = true)`; `register(...)` resolves `jwt.subject → UserId` via `UserRepositoryPort.requireBySubject` (`ReviewService` precedent) and consults `IdempotencyKeyPort` with `aggregateType="Photo"` (24h TTL). Object key shape validated against `restaurants/{restaurantId}/{uuid}` — mismatch → `InvalidObjectKeyException` → 400 `/problems/invalid-object-key` (mirrors `invalid-cursor` slug). **No outbox event, no Neo4j projector** — photos aren't in the trust graph (precedent: `InvitationService.issue()` is the existing Postgres-only write). Controller renamed `PhotoUploadUrlRestController` → `PhotoRestController` to host both `POST /upload-url` and `POST` (mirrors `ReviewRestController`'s multi-method shape); new `POST` returns 201 + Location. Duplicate `object_key` without `Idempotency-Key` → 409 via existing `DataIntegrityViolationException → conflict` handler (free). Tests: +12 unit (298), +9 IT (132). Deferred: list-by-restaurant, delete, content-type whitelist, MinIO HEAD verification, `caption`/`filename`/dims — all wait for a caller.
- [x] **M8.4 — `GET /api/v1/user/restaurants/{restaurantId}/photos/{photoId}/download-url`** — mints a short-lived presigned MinIO **GET** URL for a registered photo. Path uses `photoId` (UUID), not the raw object key — the M8.3 row already binds key ↔ id and exposing the UUID matches the `Location` header M8.3's POST returns. `PhotoService` looks up the row; cross-restaurant mismatch (photo exists but `restaurant_id` differs from the path) → 404 `/problems/not-found` via new `PhotoNotFoundException` (mirrors `RestaurantNotFoundException`), preserving "doesn't exist under that root" semantics. New `presignGet` on `PhotoStoragePort` + `PhotoMinioAdapter` (`Method.GET` swap of the existing PUT path, same `MinioException | GeneralSecurityException | IOException` → `PhotoStorageException` wrap). New `app.minio.download-url-ttl=10m` knob on `MinioProperties`, independent of `uploadUrlTtl`. `PhotoRestController` gains a third method `@GetMapping("/{photoId}/download-url")` returning 200 + `{ objectKey, downloadUrl, expiresAt }` (symmetric to `PhotoUploadUrlResponse`). Tests: +7 unit (305), +5 IT (137) — `PhotoMinioAdapterIT.presignGetReturnsUrlThatServesUploadedBytes` round-trips PUT then GET against MinIO; new `PhotoDownloadUrlRestIT` walks mint→PUT→register→download-url→GET-bytes end-to-end, plus 404-unknown / 404-cross-restaurant / 401-anonymous. Deferred: list-by-restaurant, delete, social-graph gating, MinIO HEAD verification.

`MILESTONE M8` — Restaurants have photos. Frontend uploads/downloads direct to MinIO.

---

## Phase M9 — Restaurant tags

Goal: restaurants get categorized tags so users can filter the list. Categories are fixed (food / regime / place / venue-type); tags within each category are an admin-curated controlled vocabulary. Tags project into Neo4j as `(:Tag)` nodes with `(:Restaurant)-[:HAS_TAG]->(:Tag)` edges so future M7-style traversals (e.g. "what do my friends rate among vegan terraces") can join the trust graph to the taxonomy. The README already names this shape (`Restaurant`-`HAS_TAG`-`Tag`); M9 lands it.

Original spec, preserved for context:
- **Food**: pizza, sushi, korean, …
- **Regime**: vegan-option, vegetarian-option, great-vegan-option, vegetarian-only, …
- **Place**: take-out-only, terrace, interior, …
- **Venue-type**: fast-food, restaurant, chic-restaurant, gastronomic, …

- [x] **M9.1 — Tag taxonomy CRUD** — new `tag/` vertical slice (`Tag(TagId, TagCategory, slug, label, createdAt)`, enum `TagCategory{FOOD,REGIME,PLACE,VENUE_TYPE}`). V11 migration with global `uq_tag_slug` (slug is the natural key — M9.3 will use it as `?tag=<slug>` without category disambiguation) + DB-level `CHECK (category IN …)` belt-and-suspenders. `TagRepositoryPort` ships `save`/`findById`/`findAll` only — `findBySlug` deferred until M9.2/M9.3 actually call it (ship-with-caller). `POST /api/v1/admin/tags` 201+Location; duplicate slug → 409 via existing `DataIntegrityViolationException` handler (no `GlobalExceptionHandler` edit). `GET /api/v1/public/tags` returns `{ groups: [{ category, tags: [...] }, ...] }` — always emits all four categories in enum order with empty arrays for empty buckets, so frontend has stable shape. Slug enforced as strict kebab-case at the API boundary (`@Pattern("^[a-z0-9]+(-[a-z0-9]+)*$") @Size(max=64)`) so M9.3's URL query params stay clean. **No outbox / projector / `(:Tag)` node** — Postgres-only writes; M9.2's first attach is what justifies the Neo4j node, mirroring `InvitationService.issue()` precedent. Admin role enforced at `SecurityConfig` matcher (no `@PreAuthorize`). Tests: +16 unit (321), +11 IT (149). Deferred: tag delete, tag rename, per-category filter (`?category=`), pagination, localization, admin label-edit.

- [x] **M9.2 — Attach/detach tags on a restaurant** — V12 `restaurant_tag(restaurant_id, tag_id, attached_by, attached_at)` with PK `(restaurant_id, tag_id)` as the natural-key idempotency basis and `ON DELETE CASCADE` to `restaurant`. `POST /api/v1/user/restaurants/{id}/tags/{tagId}` returns 201+Location on new attach, 200 on re-POST (mirrors M7.1 connection's `created` flag, no `Idempotency-Key` header). `DELETE /api/v1/user/restaurants/{id}/tags/{tagId}` always 204 — missing row no-op, no outbox publish. Any `ROLE_USER` can attach/detach; `attached_by` recorded for audit but not surfaced. New `RestaurantTagAttached` event embeds tag slug/category/label so `TagProjector` MERGEs `(:Tag)` node lazily without a Postgres re-read (M9.1's deferred node projection lands here). `RestaurantTagDetached` deletes the edge only — Tag node stays. **Enrichment via REST-layer 2-query** (departs from ROADMAP's `@EntityGraph` hint): `PublicRestaurantRestController.get` calls `findRestaurant.findById` + `listRestaurantTags.listFor` and combines. Keeps `Restaurant` domain model clean (no `List<Tag>` field, no cross-component model coupling, no ctor churn). `RestaurantResponse.tags` always emits a list (empty for non-enriched callers — POST 201, list endpoint pre-M9.3). Unknown tagId → 404 via new `TagNotFoundException` (mirror of `PhotoNotFoundException`). `GET /user/restaurants/{id}` enrichment skipped — endpoint doesn't exist yet. Tests: +14 unit (335), +14 IT (163). Deferred: bulk attach, detach-by-slug, ownership-check on detach, list-endpoint enrichment (M9.3), tag delete + cascade.

- [x] **M9.3 — Tag-filtered restaurant list** — `GET /api/v1/public/restaurants` accepts repeatable `?tag=<slug>` (AND-semantics). Filter implemented as two **native-SQL** `@Query`s on `RestaurantJpaRepository` (`select r.* … where r.id in (select rt.restaurant_id … group by rt.restaurant_id having count(distinct rt.tag_id) = :n)`) — no Java import of `RestaurantTagEntity`/`TagEntity` from the restaurant slice, keeping the adapters-never-reference-each-other rule clean. Unfiltered paths stay JPQL (zero churn). Cursor shape unchanged (`{createdAt, id}`) — the filter narrows the candidate set, not the order. Unknown slugs → empty page (no catalog lookup). Slug format validated at controller (`@Pattern` + `@Size`) with a `@Size(max=10)` cap on the list; format-invalid → 400. List items now carry their tags via a new batch port method `RestaurantTagRepositoryPort.findTagsByRestaurants(Collection<RestaurantId>) → Map<…>` (the caller M9.2 named explicitly). Single JPQL join in `tag/adapters/postgres/` returns `Object[]` rows grouped server-side; intra-slice, ArchUnit-clean. `RestaurantRepositoryPort.findAll` + `ListRestaurantsUseCase.list` signatures evolved to take `List<String> tagSlugs` (one signature, no overload drift). No V13 migration — V12's `idx_restaurant_tag_tag` already covers the reverse subquery walk. Tests: +9 unit (344), +12 IT (175). Deferred: OR-semantics (`?tag-any=`), tag + rating combined filters, facet counts (`availableTags` in envelope), tag-aware affinity (M7 traversal narrowed to `(:Tag)`-tagged restaurants), `RestaurantFilter` record (waits for a second filter dimension), V13 covering index (verify with `EXPLAIN ANALYZE` first).

`MILESTONE M9` — A user can browse restaurants filtered by what they want to eat, where, and how. Frontend gets a real catalog UI. The taxonomy seeds the trust graph for future "friends + tags" recommendations.


---

## Intermediate phase I4 — Restaurant detail / user detail enrichment

Goal: two screen-shapes the frontend can't render yet. The **restaurant page** wants every review with comment text **and** the author's display name (M6.5 ships the list but deferred the author enrichment). The **user page** wants the inverse cut — every restaurant a given person rated, with the review (rating + comment text), so visiting `/users/{id}` shows "what John ate and what he thought of it". The per-user read path doesn't exist at all yet; only the restaurant-side projection of reviews does. All three pieces are read-only cross-component enrichment — no outbox, no projector, no migration on I4.1, one covering index on I4.3.

- [x] **I4.1 — `authorDisplayName` enrichment on restaurant-side review reads** — lands M6.5's deferred caller. `UserRepositoryPort.findByIds(Collection<UserId>) → Map<UserId, User>` walks `JpaRepository.findAllById` (no `@Query`, no migration); exposed to consumers via new `LookupUsersUseCase` in `user/domain/ports/` + `UserLookupService @Service @Transactional(readOnly=true)`. REST consumes the *use case*, not the port directly (M9.2/M9.3 precedent — restaurant controller depends on `ListRestaurantTagsUseCase` not the tag repo). `ReviewResponse` gains a nullable `authorDisplayName` field + dual factories (`from(Review)` keeps `null`; `from(Review, displayName)` enriches) — same shape as M9.2's `RestaurantResponse.tags`, so the 3 authenticated `ReviewRestController` callers stay un-enriched (FE already knows the logged-in user's display name). `PublicReviewRestController` batch-enriches both endpoints: list dedups `authorIds` via `.distinct()` (defensive; `uq_review_restaurant_author` already caps cardinality at 1), single GET reuses the same batch with a singleton collection — one signature, zero method drift. Stale-author defensive path falls back to `null`, not 500. Tests: +8 unit (352), +5 IT (180). Deferred: enrichment on the 3 authenticated `ReviewRestController` endpoints, avatar / extended profile fields (await caller), generic `BatchLookupPort<Id, Aggregate>` (waits for a 4th caller — I4.3 restaurant batch will be the 2nd).

- [x] **I4.2 — `GET /api/v1/public/users/{userId}`** — single-user public profile returning `{id, displayName, createdAt}`. New `FindUserUseCase` in `user/domain/ports/` distinct from I4.1's `LookupUsersUseCase` (single-vs-batch split mirrors `FindReviewByAuthorUseCase` vs `ListReviewsUseCase`); thin `FindUserService @Service @Transactional(readOnly=true)` impl wraps the existing `UserRepositoryPort.findById` and throws `UserNotFoundException` on absent — REST stays dumb, no `Optional` plumbing at the boundary. New `PublicUserRestController` at `/api/v1/public/users` (first public GET in the user component; `SignupRestController` is also `/public/` but POST). `PublicUserResponse` records exactly three fields — `subject` and `email` enforced private at the boundary, never in the wire shape; verified by `responseShape_exposesOnlyThreeComponents_neverSubjectOrEmail` reflecting over record components + IT `doesNotContain("\"subject\"")` / `"\"email\""`. 404 reuses `GlobalExceptionHandler`'s existing `UserNotFoundException` mapping (`/problems/not-found`); 400 reuses the Spring `TypeMismatchException` handler (`/problems/bad-request`) — zero new exception types, zero handler changes. No migration, no outbox, no projector. Tests: +8 unit (360), +3 IT (183). Deferred: authenticated `/user/users/{id}` variant with email/joinedAt (await caller), self profile `GET /user/me`, avatar URL, review/friend counts, `/public/users` list (enumeration red flag — explicit no-go).

- [ ] **I4.3 — `GET /api/v1/public/users/{userId}/reviews`** — keyset-paginated list of every review authored by `userId`, each item carrying the rated restaurant: `{reviewId, rating, comment, createdAt, restaurant: {id, name, address}}`. New `ReviewRepositoryPort.findByAuthor(UserId, ReviewCursor, int) → Slice` mirroring M6.5's `findByRestaurant`. Batch restaurant enrichment via new `RestaurantRepositoryPort.findByIds(Collection<RestaurantId>) → Map<RestaurantId, Restaurant>` (M9.3 precedent now extended to the restaurant slice — same batch-Map shape). Cursor `{createdAt, reviewId, v=1}` reuses M6.5's `ReviewCursorCodec` (no codec churn, no fourth-caller generic-isation trigger). Unknown `userId` → empty page (list semantics; 404 is for GET-by-id). New V13 migration: covering index `idx_review_author_created_id_desc ON review(author_id, created_at desc, id desc)` mirroring V6's restaurant-side index — needed because M6.2 only indexed the restaurant axis. No outbox, no projector. Done when: walks pages without duplicates; cursor round-trips; items expose restaurant name + address; empty-page on unknown author; V13 applied.

`MILESTONE I4` — User detail and restaurant detail pages each render from a single round-trip per list, with cross-component enrichment done server-side. **Frontend ships profile + restaurant feed views.**

---

## Phase M10 — Observability & hardening

- [ ] **M10.1 — JSON logging via `logstash-logback-encoder`** — `logback-spring.xml` with `traceId`/`spanId` MDC.
- [ ] **M10.2 — OTLP exporter target** — `compose.yaml` adds an OpenTelemetry collector; verify traces arrive.
- [ ] **M10.3 — Domain metrics** — `Counter`/`Timer` on: ratings created, outbox lag, Neo4j projection latency, presigned URL minting.
- [ ] **M10.4 — OWASP Dependency-Check + Trivy** — Maven plugin + GH Action.

`MILESTONE M10` — Ready to deploy with someone watching it.

---

## Phase M11 — First production deployment

Shared infra lives in **`lepgu_infra`** (the renamed `qui-est-ce_infra` repo, hosting Caddy + Postgres + Keycloak for all `*.lepgu.fr` apps).

- [ ] **M11.0 — Rename `qui-est-ce_infra` → `lepgu_infra`** — rename the GitHub repo, update its README to "shared infra for `*.lepgu.fr`", create a `palais-divin/` subdir alongside the existing `qui-est-ce` layout. One Keycloak instance, **one realm per app** (`qui-est-ce`, `palais-divin`).
- [ ] **M11.1 — Multi-stage Dockerfile on Distroless Java 25.**
- [ ] **M11.2 — CI pipeline** — Spotless → unit + ArchUnit → integration (profiled) → scans → image build → registry push. README §10.
- [ ] **M11.3 — Wire `palais-divin` into `lepgu_infra`** — add a `palais-divin` service to `docker-compose.yml` pulling the image tag; add a `palais-divin` realm + client to Keycloak; add a Postgres database `palaisdivin` (separate from `qui-est-ce`'s DB) on the existing instance.
- [ ] **M11.4 — Caddy route for `api.palais-divin.lepgu.fr`** — add the vhost to the existing `Caddyfile`, terminate TLS, proxy to the backend container. JVM does not terminate TLS.
- [ ] **M11.5 — Smoke test script** — `scripts/smoke.sh` hits ping + an authenticated endpoint against the deployed URL.

`MILESTONE M11` — **First prod deployment.** Invitation-only beta opens.

---

## Post-launch backlog (unordered, pick when relevant)

- **Resilience4j adoption** (circuit breakers + retry + bulkhead) — add if real evidence demands it: repeated outbound failure storms in production (Keycloak / MinIO / Neo4j), or scaling beyond a single backend instance where cascading-failure containment starts to matter. Native timeouts are already in place; this is the next layer up, not a missing baseline.
- GraalVM native image investigation (deferred per CLAUDE.md — JPA + Neo4j reflection cost).
- Geo-search endpoint (`?near=lat,lon&radius=km`) using PostGIS `ST_DWithin`.
- Bulk invitation CSV import for admins.
- Soft-delete + GDPR data-export endpoint.
- Rate-limit on `/public/signup` (bucket4j or Caddy-level).
- Multi-region read replicas for Postgres.
- Neo4j Causal Cluster instead of single instance.

---

## How to work this roadmap (notes for Claude and for humans)

- **Pick the topmost unchecked task in the earliest unfinished phase.** Don't skip ahead — phases assume prior milestones.
- **One task = one PR = one session.** If a task feels bigger than that, split it in this file *before* starting work.
- **Update the checkbox in the same commit that does the work.** No "doc churn" PRs.
- **If a task's "Done when" can't be tested, the task is wrong** — rewrite it before implementing.
- **If a milestone is hit, mention it in the commit message** so future Claude sees the phase transition without re-reading the whole file.
- **Completed entries summarize what shipped and key design calls, not step-by-step work.** Keep them scannable — git history has the rest.
