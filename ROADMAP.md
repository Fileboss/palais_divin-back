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
  - Done when: `git status` on a fresh `mvn verify` shows only intentional changes.
- [x] **M0.3 — Empty ArchUnit rules class** — `src/test/java/.../architecture/ArchitectureRulesTest.java` with one passing rule (e.g. "no class in `domain/**` imports `org.springframework.*`"). README §8.2.
  - Done when: `mvn test` runs it; rule passes against the (still empty) `domain/` tree.
- [x] **M0.4 — Spotless plugin** — wire `com.diffplug.spotless:spotless-maven-plugin` with Google Java Format + import order. README §11.
  - Done when: `mvn spotless:check` passes on the existing tree; `spotless:apply` is idempotent.
- [x] **M0.5 — README: local dev prerequisites note** — one paragraph: Docker Desktop / OrbStack / Colima must be running before `mvn verify`. Symptom of missing it: `Could not find a valid Docker environment`.
  - Done when: section added to README under "Local dev".
- [x] **M0.6 — Local datasource bootstrap for `spring-boot:run`** — add `org.springframework.boot.service-connection` labels to the `postgres`, `neo4j`, and `minio` services in `compose.yaml` so Spring Boot's docker-compose support auto-injects JDBC/Neo4j/S3 connection details. Without this, `mvn spring-boot:run` fails at startup with `Failed to determine a suitable driver class` (the JPA starter is on the classpath but no DataSource URL is bound — the `postgis/postgis` image isn't in Boot's default-recognized list). Revealed by M1.1 first booting the app.
  - Done when: `./mvnw spring-boot:run` (with Docker daemon up) reaches `Started PalaisDivinBackendApplication` without errors.

`MILESTONE M0` — `mvn clean verify -P integration-tests` runs green on a fresh clone (Docker daemon up). No surprises in `git status`.

---

## Phase M1 — First end-to-end vertical (anonymous, no DB)

Goal: an HTTP request hits the running app and gets a well-formed response. Walking skeleton — wire, not feature.

- [x] **M1.1 — Public health endpoint** — `GET /api/v1/public/ping` returns `{"status":"ok","ts":<iso>}` via a controller in `shared/adapters/web/`.
  - Done when: app starts, `curl localhost:8080/api/v1/public/ping` returns 200 with the JSON shape. MockMvc test asserts it.
- [x] **M1.2 — Global `ProblemDetail` advice** — `@RestControllerAdvice` in `shared/adapters/web/` returning `application/problem+json` per RFC 9457. Handle `MethodArgumentNotValidException`, `NoResourceFoundException`, fallback `Exception`. README §6.
  - Done when: hitting an unknown path returns 404 as ProblemDetail; a controller throwing `IllegalArgumentException` returns 400 ProblemDetail; no stack trace leaks.
- [x] **M1.3 — Actuator exposure baseline** — expose `health`, `info`, `metrics`, `prometheus` under `/actuator/**`; lock everything else.
  - Done when: those four endpoints return 200; `/actuator/env` returns 404.
- [x] **M1.4 — Path-prefix `SecurityFilterChain` skeleton** — public/user/admin chain stubs, all unauthenticated for now (token validation comes in M3). README §7.1.
  - Done when: `/api/v1/public/**` is open, `/user/**` and `/admin/**` return 401 (no token yet expected).

`MILESTONE M1` — App boots, public endpoint returns 200, errors are ProblemDetail, security skeleton is in place. **You can now stand up the frontend** against a stable contract for public endpoints.

---

## Phase M2 — First persisted aggregate: `Restaurant` on Postgres

Goal: write + read one aggregate end to end. Postgres only, no Neo4j yet, no auth yet (still on `/public` for now).

- [x] **M2.1 — Flyway baseline + V1 migration** — `db/migration/V1__restaurant.sql`: enable `postgis`, create `restaurant(id uuid pk, name text, address text, location geography(Point,4326), created_at timestamptz)`, GIST index on `location`. README §5.1.
  - Done when: `mvn verify -P integration-tests` brings up PostGIS via Testcontainers and Flyway applies cleanly.
- [x] **M2.2 — `restaurant/domain` skeleton** — `Restaurant` record (or class), `RestaurantId` VO, `Coordinates` VO, `RestaurantRepositoryPort` in `domain/ports/`. JDK-only imports. Add ArchUnit rule that asserts it.
  - Done when: ArchUnit rule passes; no Spring/Jakarta imports in `domain/`.
- [x] **M2.3 — `restaurant/application/RestaurantService`** — `create(...)`, `findById(...)`. Implements a `CreateRestaurantUseCase` / `FindRestaurantUseCase` interface defined in `domain/ports/`.
  - Done when: unit tests (mocked port) pass; no Spring context loaded.
- [x] **M2.4 — `restaurant/adapters/postgres`** — JPA entity + repository implementing the domain port. Mapping `Coordinates` ↔ `geography(Point,4326)`. Also wire `@Service` on `RestaurantService` and add a `Clock` bean in `config/` (both deferred from M2.3 — couldn't ship before the port had an adapter, would break `contextLoads`).
  - Done when: a `@DataJpaTest`-style IT (Testcontainers PostGIS) round-trips a restaurant.
- [x] **M2.5 — `restaurant/adapters/rest`** — `POST /api/v1/public/restaurants` (create) + `GET /api/v1/public/restaurants/{id}`. DTOs as records.
  - Done when: MockMvc test asserts both endpoints; integration test posts then gets the same restaurant.
- [x] **M2.6 — Keyset pagination on list endpoint** — `GET /api/v1/public/restaurants?cursor=&size=&sort=`. `Slice<T>` (no `COUNT(*)`). Cursor = Base64URL of `{k, id, v}`. README §7.4.
  - Done when: list returns the envelope `{ data, page: { size, hasNext, nextCursor } }`; a test inserts 50 rows and walks pages by cursor.
- [x] **M2.7 — OpenAPI spec generated at build time** — wire springdoc-openapi so `mvn verify` produces a committed `openapi.json` (or `openapi.yaml`) covering all current endpoints. To hand off to the frontend dev between phases.
  - Done when: a fresh `mvn verify -P integration-tests` writes/refreshes the spec file at a deterministic path; the file contains every controller path currently exposed (ping, restaurant POST/GET/list); CI fails if the committed file is stale relative to the code.
- [x] **M2.8 — Server-side address geocoding (Base Adresse Nationale)** — replace the `coordinates` input on `POST /api/v1/public/restaurants` with an `address` field; resolve to `Coordinates` server-side via BAN (`https://api-adresse.data.gouv.fr/search/?q=…&limit=1`) using an `@HttpExchange` client with 2s connect+read timeout (README §7.2). Persist both the submitted address string and the resolved point. In-memory Caffeine cache (24h TTL) keyed by normalized address to avoid re-querying BAN for the same input. (Added post-M2 shipping — needed before the frontend builds its create-restaurant form so it never has to compute lat/lon. See M0.6 for the same retroactive-fix pattern.)
  - Done when: `POST` with `{ "name": "...", "address": "..." }` returns 201 with persisted coordinates; an IT verifies a known Paris address resolves within ~50m of the expected point; an unresolvable address returns 422 ProblemDetail; the BAN client is stubbed in tests (no live HTTP from CI); OpenAPI spec from M2.7 reflects the new request shape.

`MILESTONE M2` — One aggregate persisted, queryable, paginated, submitted by address (server-geocoded), with a published OpenAPI contract. Frontend can already render a list and a create form. Backend is no longer empty.

---

## Phase M3 — Authentication via Keycloak

Goal: real auth. Public stays public; user-scoped endpoints require a valid JWT.

- [x] **M3.1 — `config/security` — OAuth2 Resource Server, STATELESS** — `application.yml` issuer-uri, JWT decoder, role mapping (`realm_access.roles` → `ROLE_*`). README §7.1.
  - Done when: an unsigned request to `/user/**` returns 401 ProblemDetail; a valid Keycloak-issued token returns 200.
- [x] **M3.2 — `compose.yaml` realm bootstrap** — import a `palaisdivin` realm JSON with one test client + one test user at container start. Keep in `compose/keycloak/realm-palaisdivin.json`.
  - Done when: `docker compose up` produces a usable realm; manual token fetch works.
- [x] **M3.3 — Move `Restaurant` GETs under `/api/v1/user/restaurants`** — keep `/public` only for ping and obvious anon endpoints.
  - Done when: existing tests updated; auth required for the list/detail.
- [x] **M3.4 — Integration test with `testcontainers-keycloak`** — boot Keycloak in IT, mint a token, hit `/user/restaurants`.
  - Done when: green on `mvn verify -P integration-tests`.

`MILESTONE M3` — End-to-end **secure** vertical. **Frontend integrates the Keycloak login flow now.** This is the first "demo-able" state.

---

## Phase M4 — Transactional Outbox + Neo4j projection

Goal: dual-store sync that respects the README's eventual-consistency model.

- [x] **M4.1 — `outbox_event` table** — Flyway migration: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload jsonb`, `status`, `created_at`, `processed_at`. README §5.3.
  - Done when: migration applies; index on `(status, created_at)`.
- [ ] **M4.2 — `shared/adapters/outbox/OutboxPublisher`** — domain-facing port + JPA-backed adapter. Writes payload in the same tx as the aggregate.
  - Done when: unit test (mocked tx manager) shows aggregate save and outbox row commit atomically.
- [ ] **M4.3 — `shared/adapters/outbox/OutboxWorker`** — `@Scheduled`, `SELECT ... FOR UPDATE SKIP LOCKED LIMIT N`, dispatches to a `Projector` per `aggregate_type`, marks `PROCESSED`. Concurrency bounded by a fixed-size `ThreadPoolTaskExecutor` if/when fan-out is needed.
  - Done when: concurrent IT with 2 worker beans shows no double-processing.
- [ ] **M4.4 — `restaurant/adapters/neo4j/RestaurantProjector`** — Cypher `MERGE` of `(:Restaurant {id})` with name/coords. Idempotent.
  - Done when: IT creates a restaurant via REST, polls Neo4j with Awaitility, finds the node.

`MILESTONE M4` — Dual store is wired and provably idempotent. From here on, anything cross-component goes through the outbox by default.

---

## Phase M5 — User + Invitation

Goal: invitation-only signup via Keycloak Admin API. No self-service.

- [ ] **M5.1 — `user/domain`** — `User` aggregate, `InvitationToken` VO (24h ttl, single-use), `UserRepositoryPort`.
- [ ] **M5.2 — `user/adapters/postgres`** — JPA + V2 migration (`app_user`, `invitation`).
- [ ] **M5.3 — `user/adapters/keycloak` — `@HttpExchange` client** — Spring 7 HTTP Interface against Keycloak Admin API. 2s connect + read timeout configured on the `RestClient.Builder`. README §7.2.
  - Done when: an IT mints a Keycloak user via the client.
- [ ] **M5.4 — `POST /admin/invitations`** — admin issues a token; returns one-time signup URL.
- [ ] **M5.5 — `POST /api/v1/public/signup`** — consumes token, creates Postgres `User` + Keycloak user + outbox event for Neo4j projection.
  - Done when: e2e IT — admin invites → public signup → user can log in and hit `/user/restaurants`.

`MILESTONE M5` — Onboarding flow is real. Frontend implements the invitation/signup screens.

---

## Phase M6 — Ratings

Goal: the core domain action.

- [ ] **M6.1 — `review/domain`** — `Rating` aggregate (score, comment, restaurant_id, author_id).
- [ ] **M6.2 — `review/adapters/postgres`** — V3 migration + JPA.
- [ ] **M6.3 — `POST /api/v1/user/restaurants/{id}/ratings`** — accepts `Idempotency-Key` header (24h dedup table). README §7.5.
- [ ] **M6.4 — Outbox → Neo4j projector** — `(:User)-[:RATED {score}]->(:Restaurant)`.
- [ ] **M6.5 — `GET /api/v1/user/restaurants/{id}/ratings`** — keyset-paginated.

`MILESTONE M6` — A user can rate a restaurant, and the rating shows up in Neo4j. **First playable feature.**

---

## Phase M7 — Web of Trust (the actual product)

Goal: recommendations come from the graph, not from aggregates.

- [ ] **M7.1 — `KNOWS` edge management** — `POST /user/connections/{id}` creates the edge in Postgres + outbox + Neo4j.
- [ ] **M7.2 — `GET /user/recommendations`** — Cypher: friend-of-friend traversal, weighted by `RATED.score`, depth ≤ 2. Server-side ranking + keyset pagination.
- [ ] **M7.3 — `GET /user/restaurants/{id}/affinity`** — per-restaurant score based on which trusted users rated it.

`MILESTONE M7` — The platform's reason to exist works end-to-end.

---

## Phase M8 — Photos via MinIO presigned URLs

Goal: media without proxying bytes through Java. README §5.4.

- [ ] **M8.1 — `config/MinioConfig`** — client bean; 2s timeout configured on `MinioClient.httpClient(...)`.
- [ ] **M8.2 — `POST /user/restaurants/{id}/photos/upload-url`** — returns presigned PUT URL.
- [ ] **M8.3 — `POST /user/restaurants/{id}/photos`** — registers uploaded object key + metadata.
- [ ] **M8.4 — `GET /user/restaurants/{id}/photos/{key}/download-url`** — returns presigned GET URL.

`MILESTONE M8` — Restaurants have photos. Frontend uploads/downloads direct to MinIO.

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
  - Done when: repo is renamed; local clone updated; `qui-est-qui.lepgu.fr` still serves traffic unchanged.
- [ ] **M10.1 — Multi-stage Dockerfile on Distroless Java 25.**
- [ ] **M10.2 — CI pipeline** — Spotless → unit + ArchUnit → integration (profiled) → scans → image build → registry push. README §10.
- [ ] **M10.3 — Wire `palais-divin` into `lepgu_infra`** — add a `palais-divin` service to `docker-compose.yml` pulling the image tag; add a `palais-divin` realm + client to Keycloak; add a Postgres database `palaisdivin` (separate from `qui-est-ce`'s DB) on the existing instance.
  - Done when: `docker compose up` in `lepgu_infra` boots both apps side by side.
- [ ] **M10.4 — Caddy route for `api.palais-divin.lepgu.fr`** — add the vhost to the existing `Caddyfile`, terminate TLS, proxy to the backend container. JVM does not terminate TLS.
  - Done when: `curl https://api.palais-divin.lepgu.fr/api/v1/public/ping` returns 200 with a valid cert.
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
