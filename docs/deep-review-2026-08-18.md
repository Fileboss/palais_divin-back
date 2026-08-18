# Deep review findings — 2026-08-18

Source for ROADMAP phase **I11**. Each finding is referenced from ROADMAP as `#dr<n>`. This file is the "why" and the evidence; ROADMAP carries the "done when." Positive findings (no SQL/Cypher injection, IDOR-safe review updates, no committed secrets, clean domain-purity boundary, etc.) aren't repeated here — only actionable items.

---

## DR1 — No password strength policy (security, high)

`SignupRequest.password` (`user/adapters/rest/SignupRequest.java`) has only `@NotBlank` — a one-character password passes API validation. `compose/keycloak/realm-palaisdivin.json` (and the test realm) set no `passwordPolicy`, so nothing downstream catches it either. Invitation-only signup lowers the blast radius but doesn't remove it — this is still the account credential.

**Fix**: minimum-length/complexity constraint on `SignupRequest.password` (reject before the Keycloak round-trip), plus a `passwordPolicy` on the realm as defense in depth.

## DR2 — Neo4j driver has no connect/read timeout (security/reliability, medium)

`CLAUDE.md`'s "Outbound calls" rule requires connect+read timeouts on every native client (Keycloak, MinIO, Neo4j — default 2s). `HttpClientsConfig` and `MinioConfig` both do this; there is no `Neo4jConfig` and no `spring.neo4j.*` timeout property anywhere. If Neo4j degrades, recommendation/affinity/projection calls can block without bound.

**Fix**: explicit `org.neo4j.driver.Config` connection timeout (a `Neo4jConfig` bean, or `spring.neo4j.pool.connection-acquisition-timeout` + `spring.neo4j.connection-timeout` properties), matching the 2s default used elsewhere.

## DR3 — Keycloak brute-force protection unset (security, medium)

`bruteForceProtected` is absent from `compose/keycloak/realm-palaisdivin.json` (defaults to `false`) — no lockout on repeated failed logins. This realm only backs dev/IT; the prod realm lives in the separate `lepgu_infra` repo and wasn't reviewed here.

**Fix**: set `bruteForceProtected=true` with sane thresholds on the dev/test realm; flag the same check for whoever provisions the prod realm in `lepgu_infra`.

## DR4 — CORS ownership is undecided (security/architecture, low-medium — informational)

No `CorsConfigurationSource` or `@CrossOrigin` exists anywhere in this repo. M11.4's plan puts the API on `api.palais-divin.lepgu.fr`, a different origin than the frontend — something has to emit CORS headers, either Spring here or Caddy in `lepgu_infra`. Today the failure mode is closed (browser requests just fail), so this isn't a live vulnerability — but it's exactly the kind of gap that gets "fixed" under deadline pressure with an `allowedOrigins("*")` + credentials footgun if nobody decides ownership ahead of time.

**Fix**: decide and document who owns CORS before M11.4 ships; implement on whichever side wins.

## DR5 — Unbounded string length on a few request DTOs (quality/low-severity security, low)

`CreateRestaurantRequest.name`/`.address` and `SignupRequest.displayName` have `@NotBlank` with no `@Size` ceiling, inconsistent with `CreateReviewRequest.comment` (`@Size(max=1000)`) and tag labels (`@Size(max=127)`). Unbounded strings reach Postgres as-is.

**Fix**: add `@Size(max=…)` caps matching the pattern already used elsewhere.

## DR6 — Possible exception-message leakage in two global handlers (security, low)

`GlobalExceptionHandler.handleTypeMismatch` and `handleIllegalArgument` (`shared/adapters/web/GlobalExceptionHandler.java`) put `ex.getMessage()` directly into the public `ProblemDetail.detail`. Nothing sensitive was found in the paths sampled during this review, but nothing currently prevents a future `IllegalArgumentException` thrown with an internal-detail message from reaching an anonymous caller verbatim.

**Fix**: audit both handlers' actual call sites; sanitize or replace with a fixed detail string if any path could carry internal state.

## DR7 — ArchUnit doesn't enforce adapter isolation (architecture, medium)

`CLAUDE.md` states "Adapters never reference each other" as an ArchUnit-enforced hard rule. `ArchitectureRulesTest.java` only encodes three rules (domain framework-free, domain-only-depends-on-JDK+domain, application-doesn't-depend-on-adapters) — there is no rule for adapter-to-adapter isolation. It's already violated three times (`shared/adapters/**` cross-references are the legitimate exception and not counted here):

- `user/adapters/rest/RecommendationRestController.java` → `restaurant.adapters.rest.MissingAnchorException`
- `user/adapters/rest/RecommendationResponse.java` → `restaurant.adapters.rest.CoordinatesDto`
- `shared/adapters/web/GlobalExceptionHandler.java` → `restaurant.adapters.rest.{MissingAnchorException,AffinityRequiresAuthException}`

ROADMAP entries I6.3/I10.3 wave these off as "precedent." A rule normalized by prose instead of caught by the build will keep eroding.

**Fix**: add the ArchUnit rule (with a `shared/adapters/**` exemption), then either fix the three violations or add a small, explicit, commented allowlist so future ones require a conscious decision.

## DR8 — `RestaurantPostgresAdapter` sort-switch consolidation is not compiler-enforced (complexity, low-medium)

`RestaurantPostgresAdapter.findAll` (the largest adapter in the codebase) drives four parallel private methods keyed on `RestaurantSort` (`appendKeysetPredicate`, `bindCursorParameters`, `bindAnchorParameters`, `orderByClause`). `orderByClause` is an expression switch, so the compiler enforces exhaustiveness on it; `appendKeysetPredicate` and `bindCursorParameters` are statement switches, which Java does **not** require to be exhaustive over an enum. Today all five `RestaurantSort` values are handled correctly everywhere — but the next sort addition is one easy-to-miss arm away from a silent keyset bug that only surfaces as wrong pagination order in production, not a compile error.

**Fix**: consolidate the four switches into one per-sort strategy (e.g. a small sealed interface or a `Map<RestaurantSort, SortStrategy>`) so a missing case fails to compile instead of failing silently at runtime.

---

## Deferred (tracked in ROADMAP "Post-launch backlog," not I11)

- **DR9 — `GlobalExceptionHandler` boilerplate** (16+ near-identical `@ExceptionHandler` methods, 377 lines) is due for a data-driven collapse once it grows further. Not urgent today; noted so it doesn't get re-discovered from scratch.
