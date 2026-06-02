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

- [ ] **M8.1 — `config/MinioConfig`** — client bean; 2s timeout configured on `MinioClient.httpClient(...)`.
- [ ] **M8.2 — `POST /user/restaurants/{id}/photos/upload-url`** — returns presigned PUT URL.
- [ ] **M8.3 — `POST /user/restaurants/{id}/photos`** — registers uploaded object key + metadata.
- [ ] **M8.4 — `GET /user/restaurants/{id}/photos/{key}/download-url`** — returns presigned GET URL.

`MILESTONE M8` — Restaurants have photos. Frontend uploads/downloads direct to MinIO.

---

## Intermediate phase I4 - restaurant details / user details
 - on user detail page, front wants to display all restaurant noted by this person and the related review. Add endpoints if require or tick it directly if nothing to do
 - on restaurant page, front want to display all reviews with note text and author. same

---

## Phase M9 — Observability & hardening

- [ ] **M9.1 — JSON logging via `logstash-logback-encoder`** — `logback-spring.xml` with `traceId`/`spanId` MDC.
- [ ] **M9.2 — OTLP exporter target** — `compose.yaml` adds an OpenTelemetry collector; verify traces arrive.
- [ ] **M9.3 — Domain metrics** — `Counter`/`Timer` on: ratings created, outbox lag, Neo4j projection latency, presigned URL minting.
- [ ] **M9.4 — OWASP Dependency-Check + Trivy** — Maven plugin + GH Action.

`MILESTONE M9` — Ready to deploy with someone watching it.

---

## Phase M10 — First production deployment

Shared infra lives in **`lepgu_infra`** (the renamed `qui-est-ce_infra` repo, hosting Caddy + Postgres + Keycloak for all `*.lepgu.fr` apps).

- [ ] **M10.0 — Rename `qui-est-ce_infra` → `lepgu_infra`** — rename the GitHub repo, update its README to "shared infra for `*.lepgu.fr`", create a `palais-divin/` subdir alongside the existing `qui-est-ce` layout. One Keycloak instance, **one realm per app** (`qui-est-ce`, `palais-divin`).
- [ ] **M10.1 — Multi-stage Dockerfile on Distroless Java 25.**
- [ ] **M10.2 — CI pipeline** — Spotless → unit + ArchUnit → integration (profiled) → scans → image build → registry push. README §10.
- [ ] **M10.3 — Wire `palais-divin` into `lepgu_infra`** — add a `palais-divin` service to `docker-compose.yml` pulling the image tag; add a `palais-divin` realm + client to Keycloak; add a Postgres database `palaisdivin` (separate from `qui-est-ce`'s DB) on the existing instance.
- [ ] **M10.4 — Caddy route for `api.palais-divin.lepgu.fr`** — add the vhost to the existing `Caddyfile`, terminate TLS, proxy to the backend container. JVM does not terminate TLS.
- [ ] **M10.5 — Smoke test script** — `scripts/smoke.sh` hits ping + an authenticated endpoint against the deployed URL.

`MILESTONE M10` — **First prod deployment.** Invitation-only beta opens.

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
