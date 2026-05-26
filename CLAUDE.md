# CLAUDE.md

Guidance for Claude Code working in this repo. `README.md` is the authoritative spec — defer to it when conflicts arise.

## Project state

Phases M0 + M1 complete (scaffold + walking skeleton: ping, ProblemDetail, actuator, security stub). `ROADMAP.md` is the source of truth for what comes next and per-task done-when criteria — pick the topmost unchecked task in the earliest unfinished phase. Package root: `fr.lepgu.palaisdivin.backend`.

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
│   ├── domain/{exception,valueobject}
│   └── adapters/{outbox,web}           # Transactional Outbox + ProblemDetail handler
└── config/                             # Security, Observability
```

Hard rules (ArchUnit-enforced — README §8):
- `domain/**` imports nothing from `org.springframework.*`, `jakarta.*`, `org.neo4j.*`, `io.minio.*`. JDK only.
- Use-case **interfaces** in `domain/ports/` (flat — no `in/`/`out/`); **implementations** in `application/`.
- No cross-component imports (`restaurant` ↛ `review`) — go through the outbox.
- Adapters never reference each other; `application/**` never references `adapters/**`.

## Data: dual store, eventually consistent

- **Postgres + PostGIS** = source of truth. Holds users, fiches, invitations, outbox table, Keycloak's own schema. All geospatial filtering runs in SQL with GIST indexes.
- **Neo4J** = derived view for the social graph (`User`-`KNOWS`-`User`, `User`-`RATED`-`Restaurant`, `Restaurant`-`HAS_TAG`-`Tag`). Affinity scoring is Cypher, not Java.
- **Sync = Transactional Outbox**, NOT bare `@TransactionalEventListener`. Business op writes aggregate + `outbox_event` row in the same Postgres tx; `@Scheduled` worker drains with `SKIP LOCKED`, projects via Cypher `MERGE` (idempotent), marks `PROCESSED`.
- Treat Neo4J as eventually consistent — no read-your-writes cross-store in one request. Tests assert projection via `Awaitility`, never `Thread.sleep`.
- **MinIO** for binaries — backend issues presigned URLs; the frontend uploads/downloads direct. Never proxy bytes through Java.

When adding a feature: geospatial → Postgres; friend-of-friend/affinity → Neo4J; both → write Postgres + outbox row, let the worker project.

## Runtime & framework

- **Java 25 LTS** (Java 26 is preview). Virtual threads on (`spring.threads.virtual.enabled=true`). Use `ReentrantLock` not `synchronized` around I/O (pinning). `ScopedValue` instead of `ThreadLocal` for request context. `StructuredTaskScope` for parallel fan-outs.
- **Records for DTOs**, `sealed` interfaces for aggregate states + transverse exceptions, pattern-matching switches.
- **Spring Boot 4.0.6** (Spring Framework 7, Jakarta EE 11). Use: `@HttpExchange` HTTP Interface clients (Keycloak Admin API), `@ServiceConnection` (Testcontainers), Docker Compose support in dev, scoped `@EnableJpaRepositories` / `@EnableNeo4jRepositories`.
- **OAuth2 Resource Server**, `STATELESS`. Backend never handles passwords — user creation goes via Keycloak Admin API from `user/adapters/keycloak/`.

## Boot 4 bite-once gotchas

- Web starter is `spring-boot-starter-webmvc` (not `-web`); test slice annotations moved to `org.springframework.boot.webmvc.test.autoconfigure.*` (e.g. `@WebMvcTest`, `@AutoConfigureMockMvc`). `@LocalServerPort` is at `org.springframework.boot.test.web.server.LocalServerPort`.
- `TestRestTemplate` requires `@AutoConfigureTestRestTemplate` + `spring-boot-restclient` on the test classpath. Prefer `RestClient.create("http://localhost:" + port)` with `@LocalServerPort` for full-HTTP integration tests — no extra deps, no test-client annotations.
- `@SpringBootTest` substitutes `SimpleMeterRegistry` for the Prometheus registry by default. Metrics-export tests need `@ImportAutoConfiguration(PrometheusMetricsExportAutoConfiguration.class)` + `management.endpoint.prometheus.access=read-only` + `management.prometheus.metrics.export.enabled=true` to mirror prod.
- A custom `SecurityFilterChain` with no auth mechanism returns **403** (not 401) for unauthenticated requests. Stub chains need `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`; M3's `.oauth2ResourceServer()` then replaces it with `BearerTokenAuthenticationEntryPoint`.
- `spring-boot-docker-compose` auto-detects only vanilla images (`postgres`, `neo4j`, `minio`). Non-vanilla images (`postgis/postgis`) need `org.springframework.boot.service-connection: <type>` labels in `compose.yaml`.

## Cross-cutting

- **Errors**: RFC 9457 `ProblemDetail` (`application/problem+json`) via a `@RestControllerAdvice` in `shared/adapters/web/`. Never leak stack traces.
- **Observability**: Micrometer Tracing + OTLP, Logback JSON with `traceId`/`spanId`, domain-specific metrics.
- **Outbound calls** (Keycloak, MinIO, Neo4J): configure connect + read timeouts on the native client (`RestClient.Builder` for Keycloak, `MinioClient.httpClient(...)` for MinIO, `org.neo4j.driver.Config.builder()` for Neo4J). Default 2s. No Resilience4j — see `ROADMAP.md` "Post-launch backlog" if/when CB/retry/bulkhead become evidence-driven needs.
- **Config**: `@ConfigurationProperties` + `@Validated`, no scattered `@Value`. Secrets via env / Docker secrets only.

## API conventions

- Auth-by-prefix: `/api/v1/public/**` (anon), `/api/v1/user/**` (`ROLE_USER`), `/api/v1/admin/**` (`ROLE_ADMIN`).
- **List endpoints: keyset (cursor) pagination only.** Params: `cursor` (opaque Base64URL), `size` (default 20, max 100, `@Max`-validated), `sort` (server-side enum whitelist). Response envelope: `{ data, page: { size, hasNext, nextCursor } }`. Use Spring Data `Slice<T>` (no `COUNT(*)`). Cursor encodes `{k, id, v}` — `id` tiebreaker is mandatory.
- Server-side filtering/sorting/geospatial ranking always — frontend just renders.
- User-endpoint mutations accept `Idempotency-Key` (24h dedup).
- No self-service signup — admin-issued invitation tokens only.

## Testing

- **Unit (~70%)**: `domain/` + `application/` (mocked ports). JUnit 5 + AssertJ + Mockito, no Spring context, <10ms per test.
- **ArchUnit (~5%)**: enforce isolation rules at build time.
- **Integration (~25%)**: Testcontainers + `@ServiceConnection`, shared-container pattern, `testcontainers.reuse.enable=true` locally. Containers: `postgis/postgis:16-3.4`, `neo4j:5.20-enterprise`, `minio/minio`, Keycloak.
- Gated: unit + ArchUnit on `mvn clean test`; integration on `mvn verify -P integration-tests`.

## CI / deploy

Pipeline: Spotless → unit + ArchUnit → integration (profiled) → OWASP Dependency-Check + Trivy → multi-stage Docker on **Distroless Java 25** → registry push. GraalVM native image deferred (JPA+Neo4j reflection cost). Caddy terminates TLS — the JVM does not. Infra deploys are GitOps from a separate repo.
