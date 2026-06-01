# CLAUDE.md

Guidance for Claude Code working in this repo. `README.md` is the authoritative spec — defer to it when conflicts arise. `ROADMAP.md` is the source of truth for phase status: pick the topmost unchecked task in the earliest unfinished phase. Package root: `fr.lepgu.palaisdivin.backend`.

## Domain in one paragraph

Invitation-only restaurant rating platform built around a Web of Trust. Ratings and recommendations are scoped to the user's social graph, not public — recommendations come from graph traversal over the user's network, not aggregate scores.

## Architecture: vertical-slice hexagonal

```
fr.lepgu.palaisdivin.backend
├── <component>/                # restaurant, review, user
│   ├── domain/{model,service,ports}   # pure Java, no framework imports
│   ├── application/                    # @Service impls of use cases (orchestration)
│   └── adapters/{rest,postgres,neo4j,…}
├── shared/
│   ├── domain/{exception,valueobject,ports}
│   └── adapters/{outbox,postgres,web}  # Transactional Outbox, Idempotency-Key, ProblemDetail handler
└── config/                             # Security, Observability
```

Hard rules (ArchUnit-enforced — README §8):
- `domain/**` imports nothing from `org.springframework.*`, `jakarta.*`, `org.neo4j.*`, `io.minio.*`. JDK only.
- Use-case **interfaces** in `domain/ports/` (flat — no `in/`/`out/`); **implementations** in `application/`.
- No cross-component imports for writes (`restaurant` ↛ `review`) — go through the outbox. Sync read-only cross-component imports of another component's `domain/ports/*` are OK (precedent: M6.3 `ReviewService` reads `User` + `Restaurant` directly); cross-component `application/*` and `adapters/*` stays forbidden.
- Adapters never reference each other; `application/**` never references `adapters/**`.
- **Ship with caller.** Don't add a port method, exception, DTO, or migration until something asks for it. Method names on ports show up only when their use case is being implemented. Generic abstractions (`CursorCodec<T>`, shared `PageMeta`) wait for the third caller, not the second.

## Data: dual store, eventually consistent

- **Postgres + PostGIS** = source of truth. Users, restaurants, reviews, invitations, outbox, idempotency keys, Keycloak's own schema. Geospatial filtering runs in SQL with GIST indexes.
- **Neo4J** = derived view for the social graph (`User`-`KNOWS`-`User`, `User`-`RATED`-`Restaurant`, `Restaurant`-`HAS_TAG`-`Tag`). Affinity scoring is Cypher, not Java.
- **Sync = Transactional Outbox**, NOT bare `@TransactionalEventListener`. Business op writes aggregate + `outbox_event` row in the same Postgres tx; `@Scheduled` worker drains with `SKIP LOCKED`, projects via Cypher `MERGE` (idempotent), marks `PROCESSED`.
- **Projectors `MERGE` both endpoints, not `MATCH`.** A `Review` event can drain before its `User`/`Restaurant` projections — `MATCH` would silently no-op and `markProcessed` would lose the projection forever. `MERGE (u:User {id}) MERGE (r:Restaurant {id}) MERGE (u)-[…]->(r)` leaves id-only stubs that the dependent projectors enrich later.
- Treat Neo4J as eventually consistent — no read-your-writes cross-store in one request. Tests assert projection via `Awaitility`, never `Thread.sleep`.
- **MinIO** for binaries — backend issues presigned URLs; the frontend uploads/downloads direct. Never proxy bytes through Java.

## Runtime & framework

- **Java 25 LTS** (Java 26 is preview). Virtual threads on (`spring.threads.virtual.enabled=true`). Use `ReentrantLock` not `synchronized` around I/O (pinning). `ScopedValue` instead of `ThreadLocal` for request context. `StructuredTaskScope` for parallel fan-outs.
- **Records for DTOs**, `sealed` interfaces for aggregate states + transverse exceptions, pattern-matching switches.
- **Spring Boot 4.0.6** (Spring Framework 7, Jakarta EE 11). Use: `@HttpExchange` HTTP Interface clients (Keycloak Admin API), `@ServiceConnection` (Testcontainers), Docker Compose support in dev.
- **OAuth2 Resource Server**, `STATELESS`. Backend never handles passwords — user creation goes via Keycloak Admin API from `user/adapters/keycloak/`.

## Boot 4 bite-once gotchas

Real session-costs — recognise the symptoms when you see them.

- Web starter is `spring-boot-starter-webmvc` (not `-web`); test slice annotations live at `org.springframework.boot.webmvc.test.autoconfigure.*` (`@WebMvcTest`, `@AutoConfigureMockMvc`). `@LocalServerPort` is at `org.springframework.boot.test.web.server.LocalServerPort`. `@DataJpaTest` is at `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.
- For full-HTTP ITs, prefer `RestClient.create("http://localhost:" + port)` with `@LocalServerPort` over `TestRestTemplate` — no extra deps, no client-config annotations.
- `@SpringBootTest` substitutes `SimpleMeterRegistry` for the Prometheus registry. Metrics-export tests need `@ImportAutoConfiguration(PrometheusMetricsExportAutoConfiguration.class)` + `management.endpoint.prometheus.access=read-only` + `management.prometheus.metrics.export.enabled=true`.
- A custom `SecurityFilterChain` with no auth mechanism returns **403** (not 401) for unauthenticated requests — stub chains need `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`.
- **OAuth2 Resource Server entry point splits in two.** `.exceptionHandling().authenticationEntryPoint(...)` only catches the "no Bearer token" path; **invalid-token 401s use the inner `BearerTokenAuthenticationEntryPoint` and skip your delegating entry point**, returning a bare 401 + `WWW-Authenticate` with no body. Wire the delegating entry point on BOTH `.exceptionHandling()` AND `.oauth2ResourceServer()` so every 401 becomes a ProblemDetail.
- **`DynamicPropertyRegistry` is NOT autowirable into `@Bean` factory params** in this Boot version (despite Spring 6.2+ docs implying otherwise). Only beans implementing `DynamicPropertyRegistrar` are seen by `DynamicPropertyRegistrarBeanInitializer`. To register dynamic properties from a container, expose a `DynamicPropertyRegistrar` bean that takes the container as a dependency.
- **`dasniko/testcontainers-keycloak`'s `KeycloakContainer` forbids `.withCommand(...)`** ("You are trying to set custom container commands"). Use `.withRealmImportFile("/realm.json")` — loads the realm from the test classpath and auto-adds `--import-realm`. `MountableFile.forHostPath(...)` + `.withCommand(...)` is a dead end.
- Keycloak 24+ declarative user-profile rejects empty `lastName`; `temporary=true` passwords yield "Account is not fully set up" on password grant. Set `lastName = displayName` and `credential.temporary = false` when creating users via Admin API.
- `spring-boot-docker-compose` auto-detects only vanilla images (`postgres`, `neo4j`, `minio`). Non-vanilla images (`postgis/postgis`) need `org.springframework.boot.service-connection: <type>` labels in `compose.yaml`.
- **`Slice<T>` + `Pageable` is incompatible with `FOR UPDATE SKIP LOCKED` queue draining.** `Slice<>` fetches `size+1` rows internally for `hasNext()` — the speculative extra row interferes with the SKIP LOCKED race when multiple workers drain in parallel (rows leak between candidate sets → double-processing). For queue patterns use `List<T>` + Spring Data 3.2+ `Limit`. `Slice<T>` remains correct for keyset-pagination request layer.
- **Adding the first `Projector` `@Component` flips `OutboxWorker` from inert (`@ConditionalOnBean`) to active in every `@SpringBootTest`.** Tests asserting `outbox_event.status='PENDING'` then race the worker — `application-test.properties` now sets `spring.task.scheduling.enabled=false` as the default. Test-side `Projector` impls must NOT claim the same `aggregateType()` as any prod projector, or `List<Projector>` autowire fails with `IllegalStateException: Duplicate key` inside the worker's `Collectors.toUnmodifiableMap`.
- **`em.flush()` in a `@DataJpaTest` bypasses Spring's `@Repository` exception translation** — a unique-constraint violation surfaces as raw Hibernate `ConstraintViolationException`, not Spring's `DataIntegrityViolationException`. Assert on the constraint *name* (`hasMessageContaining("uq_…")`), not on the exception class — more robust and more legible.

## Cross-cutting

- **Errors**: RFC 9457 `ProblemDetail` (`application/problem+json`) via `@RestControllerAdvice` in `shared/adapters/web/`. Never leak stack traces. Cursor-format errors → `shared/adapters/web/InvalidCursorException` → 400 `/problems/invalid-cursor`.
- **Observability**: Micrometer Tracing + OTLP, Logback JSON with `traceId`/`spanId`, domain-specific metrics.
- **Outbound calls** (Keycloak, MinIO, Neo4J): configure connect + read timeouts on the native client (`RestClient.Builder`, `MinioClient.httpClient(...)`, `org.neo4j.driver.Config.builder()`). Default 2s. No Resilience4j — see `ROADMAP.md` "Post-launch backlog" if/when CB/retry/bulkhead become evidence-driven.
- **Config**: `@ConfigurationProperties` + `@Validated`, no scattered `@Value`. Secrets via env / Docker secrets only.

## API conventions

- Auth-by-prefix: `/api/v1/public/**` (anon), `/api/v1/user/**` (`ROLE_USER`), `/api/v1/admin/**` (`ROLE_ADMIN`).
- **Default catalog reads to `/public/**`.** Anything that doesn't leak per-user data — restaurant list/detail, reviews on a restaurant, future tags, search results — goes there so anonymous visitors can browse without login. Move under `/user/**` only when the response is user-scoped (own profile, own ratings, friend graph) or when it mutates state. Writes are always authenticated; admin operations live under `/admin/**`.
- **List endpoints: keyset (cursor) pagination only.** Params: `cursor` (opaque Base64URL), `size` (default 20, max 100, `@Min(1) @Max(100)`-validated), `sort` (server-side enum whitelist). Envelope: `{ data, page: { size, hasNext, nextCursor } }`. Use Spring Data `Slice<T>` (no `COUNT(*)`). Cursor encodes `{k, id, v}` (`v` is integer version) — `id` tiebreaker is mandatory. List queries with an unknown filter value (e.g. nonexistent `restaurantId`) return an empty page, **not 404** — 404 is for GET-by-id.
- Server-side filtering/sorting/geospatial ranking always — frontend just renders.
- User-endpoint mutations accept `Idempotency-Key` (24h dedup) via the generic `IdempotencyKeyPort` + `idempotency_key` table — `aggregateType` discriminates callers; don't fork per-aggregate.
- No self-service signup — admin-issued invitation tokens only.

## Testing

- **Unit (~70%)**: `domain/` + `application/` (mocked ports). JUnit 5 + AssertJ + Mockito, no Spring context, <10ms per test.
- **ArchUnit (~5%)**: enforce isolation rules at build time.
- **Integration (~25%)**: every IT extends `AbstractIntegrationTest` (single shared Spring context, singleton Testcontainers started via `Startables.deepStart(...)`, `.withReuse(true)` where the container allows it). Containers: `postgis/postgis:16-3.4`, `neo4j:5.20-enterprise`, `minio/minio`, Keycloak via `dasniko/testcontainers-keycloak` (not `@ServiceConnection`-compatible — see gotchas). Stubs (BAN geocoder, blocking/failing projectors) live in `SharedTestStubs`.
- Gated: unit + ArchUnit on `mvn clean test`; integration on `mvn verify -P integration-tests`.

## CI / deploy

Pipeline: Spotless → unit + ArchUnit → integration (profiled) → OWASP Dependency-Check + Trivy → multi-stage Docker on **Distroless Java 25** → registry push. GraalVM native image deferred (JPA + Neo4j reflection cost). Caddy terminates TLS — the JVM does not. Infra deploys are GitOps from `lepgu_infra` (separate repo, shared with `qui-est-ce`).
