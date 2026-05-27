Spécifications Techniques & Architecturales du Backend — Le Palais Divin
1. Contexte, Vision & Objectifs Métier

Le projet Le Palais Divin (palaisdivin.lepgu.fr) est une plateforme communautaire et sélective de notation, de référencement et de recommandation de restaurants. Face aux limites des solutions généralistes du marché (telles que Google Maps ou TripAdvisor), qui souffrent d'une profusion de faux avis, d'un ciblage de tags imprécis (ex. un établissement référencé "Vegan" qui ne propose qu'une seule option sur sa carte), et d'une absence de contextualisation sociale, Le Palais Divin introduit un paradigme de Réseau de Confiance (Web of Trust).  
Objectifs Clés :

    Fiabilité des Données : Garantir des informations qualifiées, à jour et strictement filtrées.  

    Curation Sociale : Restreindre la visibilité des notes et des avis à un cercle de confiance (famille, amis proches) afin d'assurer l'authenticité et la pertinence des recommandations.  

    Moteur de Suggestion Graphe : Exploiter la puissance des bases de données orientées graphe pour générer des suggestions ultra-personnalisées basées sur les affinités de son réseau relationnel.  

    Sécurisation par Invitation : L'inscription à la plateforme est strictement soumise à un système d'invitation contrôlé par les administrateurs pour préserver l'intégrité de la communauté initiale.  

2. Architecture des Données & Alignement DDIA

En accord avec les principes de l'ouvrage Designing Data-Intensive Applications (DDIA), l'architecture sépare clairement le stockage de confiance transactionnel (Single Source of Truth) du stockage analytique dérivé dédié aux relations complexes (Graphe).  
2.1. PostgreSQL : Base Transactionnelle & Spatiale (Master Store)

PostgreSQL agit comme la source de vérité pour l'ensemble des données métiers structurées et immuables.

    Rôle : Stockage des utilisateurs, des fiches détaillées des restaurants (nom, adresse, métadonnées de contact, horaires), des menus, des invitations et des configurations système. Il héberge également la base de données technique de Keycloak.  

    Recherche Spatiale (PostGIS) : L'extension PostGIS est impérativement utilisée pour gérer la géolocalisation. Toutes les opérations de calcul de distance ("restaurants à moins de X km de ma position") et de requêtes géospatiales se font au niveau de la couche SQL via des index GIST.  

2.2. Neo4J : Moteur de Recommandation (Graph Store)

Neo4J modélise le réseau social et le graphe d'intérêt. C'est une vue dérivée optimisée pour le calcul de parcours.

    Rôle : Modélisation des relations.  

    Schéma Conceptuel du Graphe :

        Nodes : (u:User), (r:Restaurant), (t:Tag)

          

        Edges :

            (u1:User)-[:KNOWS]->(u2:User) (Réseau d'amis)  

            (u:User)-[:RATED {score: 4, comment: "..."}]->(r:Restaurant) (Évaluations)  

            (r:Restaurant)-[:HAS_TAG]->(t:Tag) (Catégorisation stricte : Vegan, Pizza, SansGluten)  

2.3. MinIO : Stockage d'Objets S3

    Rôle : Stockage des assets binaires non structurés (photos des restaurants, images des menus, avatars).  

    Implémentation : Le backend génère des URL signées à durée limitée (Presigned URLs) pour permettre au frontend (Svelte) d'uploader ou d'afficher directement les images depuis MinIO sans surcharger la bande passante du serveur Java.  

2.4. Stratégie de Synchronisation (PostgreSQL ↔ Neo4J) — Transactional Outbox

Un simple `@TransactionalEventListener(AFTER_COMMIT)` perd les événements en cas de crash entre le commit Postgres et l'exécution du listener. On utilise donc le **pattern Transactional Outbox** :

1.  L'opération métier écrit l'agrégat ET une ligne dans la table `outbox_event` (payload JSON + agrégat + statut `PENDING`) **dans la même transaction Postgres**. Atomicité garantie par la DB.
2.  Un worker asynchrone (Spring `@Scheduled` + lock pessimiste `SKIP LOCKED`, ou poll-and-publish) draine la table par lots, projette vers Neo4J via les adaptateurs OGM, puis marque l'événement `PROCESSED`.
3.  Idempotence côté Neo4J via `MERGE` Cypher sur les clés métiers — un événement rejoué ne crée pas de doublon.
4.  Les événements en erreur passent en `FAILED` après N tentatives (compteur en colonne sur la ligne outbox + backoff exponentiel borné dans `OutboxWorker`) et sont escaladés via métrique + alerte.

Conséquence pour les développeurs : **Neo4J est eventually consistent** (latence typique < 1s, SLA < 10s). Aucun read-your-writes cross-store dans la même requête HTTP. Les tests d'intégration qui valident la projection doivent attendre via `Awaitility`.
3. Découpage Responsabilités Front ↔ Back
   3.1. Responsabilités du Backend (API Core)

Le backend est le seul garant des règles métiers, de la sécurité et du traitement lourd des données.

    Tri et Filtrage Géospatial / Multicritères : Les opérations de filtrage (tags=vegan) et de tri par distance ou par note moyenne doivent impérativement être exécutées côté Backend.

    Calcul des Recommandations : Le calcul du score d'affinité est délégué aux requêtes Cypher de Neo4J.

### 3.1.1. Pagination — Keyset (Seek) obligatoire

L'offset pagination (`LIMIT n OFFSET m`) est **proscrite** : coût en O(n+m) côté DB, résultats incohérents si la donnée change entre deux pages, et casse complètement sur les requêtes géospatiales triées par distance calculée. Toutes les API de listage utilisent une **pagination par curseur (keyset / seek method)**.

**Contrat unifié des query params :**

| Paramètre | Type | Défaut | Max | Description |
|-----------|------|--------|-----|-------------|
| `cursor`  | string (Base64URL) | `null` (première page) | — | Token opaque encodant la clé de tri du dernier élément renvoyé. Les clients ne le décodent jamais. |
| `size`    | int    | `20`   | `100` (validé via `@Max`, sinon `400 Bad Request`) | Taille de page. |
| `sort`    | enum (whitelist serveur) | dépend de l'endpoint | — | Ex : `distance`, `rating`, `name`. Pas de sort libre depuis le client. |

**Encodage du curseur :** JSON minimal `{ "k": <clé_principale>, "id": <uuid_tiebreaker>, "v": 1 }` puis Base64URL. La clé inclut **toujours** un tiebreaker (`id`) pour garantir un ordre total stable même en cas d'égalité sur la clé principale (distance arrondie, score identique, etc.). Le champ `v` permet de faire évoluer le format sans casser les clients.

**Côté Spring Data :** on utilise `Slice<T>` (et non `Page<T>`) pour éviter la requête `COUNT(*)` — inutile et coûteuse dès que le dataset grossit. Les requêtes JPA/Cypher sont écrites en `WHERE (sort_key, id) > (:lastKey, :lastId) ORDER BY sort_key, id LIMIT :size+1` (le `+1` détermine `hasNext` sans count).

**Format de réponse unifié (toutes les listes) :**

```json
{
  "data": [ /* éléments */ ],
  "page": {
    "size": 20,
    "hasNext": true,
    "nextCursor": "eyJrIjoxMjM0LjUsImlkIjoiYWJjLTEyMyIsInYiOjF9"
  }
}
```

Quand `hasNext` est `false`, `nextCursor` est absent. Pas de `totalElements`/`totalPages` : leur calcul nécessite un COUNT et n'a pas de sens stable sur un dataset qui change.

3.2. Responsabilités du Frontend (Svelte)

    Affichage & UI/UX : Rendu des cartes, gestion des formulaires, états de chargement.  

    Géolocalisation Client : Capture des coordonnées de l'utilisateur (avec consentement) et transmission de la latitude/longitude au backend.  

    Stockage Local : Gestion sécurisée du cycle de vie des tokens d'authentification.  

4. Stack Applicative & Meilleures Pratiques (Mai 2026)

**Synthèse des bibliothèques structurantes** (détails en §4.x et §10) :

| Bibliothèque | Version | Rôle |
|---|---|---|
| **Spring Boot** | 4.0.6 | Cadre applicatif (Spring Framework 7, Jakarta EE 11). |
| **Spring Security + OAuth2 Resource Server** | Boot BOM | Validation JWT Keycloak, sessions `STATELESS`. |
| **Spring Data JPA + driver PostgreSQL** | Boot BOM | Source de vérité — agrégats, géospatial (PostGIS), table outbox. |
| **Spring Data Neo4j** | Boot BOM | Vue dérivée — graphe social, scoring d'affinité (Cypher). |
| **Flyway** | Boot BOM | Versioning du schéma Postgres. |
| **MinIO Java SDK** | 8.5.17 | Client S3, génération de presigned URLs (jamais de proxy d'octets). |
| **Micrometer Tracing + OpenTelemetry (OTLP)** | Boot BOM | Traces distribuées Tomcat → JPA → Neo4j → MinIO → Keycloak. |
| **Logstash Logback Encoder** | 8.0 | Logs JSON structurés avec `traceId` / `spanId`. |
| **JUnit 5 + AssertJ + Mockito** | Boot BOM | Tests unitaires (`domain/`, `application/`) — runner, assertions fluentes, mocks de ports (§8.1). |
| **ArchUnit** | 1.4.0 | Tests d'architecture — gardiens du découpage hexagonal au build (§8.2). |
| **Testcontainers + Awaitility** | Boot BOM | Conteneurs éphémères en IT + assertions async sur la projection outbox (§8.3). |
| **Maven Surefire** | Boot BOM | Plugin Maven exécutant les tests unitaires (`*Test.java`) en phase `test` ; rapports XML sous `target/surefire-reports/`. Complément de Failsafe (`*IT.java`, profil `integration-tests`, §10.4) — la séparation garantit qu'un IT lent ne pénalise pas la boucle dev `mvn test`. |
| **Spotless + Google Java Format** | 2.46.1 / 1.28.0 | Lint et formatage automatiques, échec build sur écart (§9.1). |

   4.1. Java 25 (LTS) & Configuration Runtime

   Cible LTS courante en 2026. Java 26 (preview, GA prévue Sept. 2026) n'est pas retenu pour la prod.

   **Virtual Threads (stables depuis Java 21)** : activés via `spring.threads.virtual.enabled=true`. Le pool Tomcat tourne en virtual threads → un appel bloquant Postgres/Neo4J/MinIO ne mobilise plus un thread plateforme. Important : **pas de `synchronized` autour d'opérations I/O** (pinning), utiliser `ReentrantLock` ; et **purger les `ThreadLocal`** côté librairies tierces.

   **Structured Concurrency (stable Java 25)** : `StructuredTaskScope` pour les fan-out parallèles (ex. enrichir un restaurant avec sa note Postgres + son score d'affinité Neo4J en parallèle), avec annulation propagée et timeout global.

   **Scoped Values (remplacent ThreadLocal sous virtual threads)** : transport du contexte requête (correlation-id, userId) sans pinning ni fuite.

   **Language features** : Records pour tous les DTOs et value objects, Pattern Matching (`switch` sur types scellés pour les exceptions métier), `sealed` interfaces pour modéliser les états d'un agrégat (`Invitation.Pending | Accepted | Expired`).

4.2. Spring Boot 4.0.6 (Spring Framework 7, Jakarta EE 11)

    **Spring Security & Keycloak** : Resource Server OAuth2 avec validation JWT (JWKS endpoint cache 10 min). Mapping `realm_access.roles` → `ROLE_*` via `JwtAuthenticationConverter` custom. Aucune session HTTP : `SessionCreationPolicy.STATELESS`.

    **Spring Data JPA & Spring Data Neo4j** : coexistence avec `@EnableJpaRepositories(basePackages = "...postgres")` et `@EnableNeo4jRepositories(basePackages = "...neo4j")` scopés pour éviter tout chevauchement.

    **HTTP Interface Clients (Spring 6.x)** : l'appel à l'API Admin Keycloak passe par une interface déclarative `@HttpExchange` (typesafe, testable) — pas de `RestTemplate` ni `WebClient` manuel.

    **Spring Boot Docker Compose support** : `compose.yaml` à la racine démarre Postgres+PostGIS, Neo4J, MinIO, Keycloak automatiquement en `mvn spring-boot:run` (dev only — désactivé en prod via `spring.docker.compose.enabled=false`).

    **`@ServiceConnection`** : les Testcontainers sont câblés au contexte Spring sans `@DynamicPropertySource`.

4.3. Observabilité, Timeouts & Configuration

   **Observabilité** : Spring Boot Actuator + **Micrometer Tracing avec exporter OpenTelemetry (OTLP)**. Traces propagées sur l'ensemble Tomcat → JPA → Neo4J driver → HTTP client Keycloak → S3 client MinIO. Logs en JSON (Logback `LogstashEncoder`) avec `traceId`/`spanId` injectés. Métriques métiers explicites : `invitations.issued`, `reviews.submitted`, `recommendations.served{cache=hit|miss}`.

   **Timeouts** : aucun appel externe sans timeout — défaut 2s, configurable par adaptateur. Configuration sur les clients natifs (`RestClient.Builder` pour Keycloak, `MinioClient.httpClient(...)` pour MinIO, `org.neo4j.driver.Config.builder()` pour Neo4J). Circuit breakers / retry / bulkhead non retenus pour le MVP — voir `ROADMAP.md` backlog post-launch si l'instabilité ou la mise à l'échelle multi-instance les justifient.

   **Configuration** : `@ConfigurationProperties` typés et **validés** (`@Validated` + Bean Validation). Aucun `@Value` éparpillé. Secrets injectés via variables d'environnement (Docker secrets en prod), jamais en clair dans `application.yml`. Profils Spring : `dev`, `test`, `prod`.

5. Architecture Logicielle : Approche Hexagonale Verticale

Le projet adopte une Architecture Hexagonale par Composant Métier (Vertical Slicing). Ce découpage garantit une lecture "orientée métier" de l'application tout en isolant la logique centrale des frameworks. Les dossiers superflus sont supprimés pour une hiérarchie plus plate et directe au niveau des ports et des adaptateurs.

**Distinction interfaces (`ports/`) vs implémentations (`application/`)** : les **use case interfaces** déclarent le contrat appelé par le REST ; leurs **implémentations** (services applicatifs orchestrant le domaine et les ports sortants) vivent dans `application/`. Cela évite que `domain/ports/` connaisse les détails d'orchestration et permet d'unit-tester l'orchestration sans Spring.

Structuration des Packages :
Plaintext

fr.lepgu.palaisdivin.backend
│
├── restaurant/                     # --- COMPOSANT MÉTIER : RESTAURANT ---
│   ├── domain/                     # Cœur de l'hexagone (Pur Java, 0 framework)
│   │   ├── model/                  # Agrégats, value objects (Restaurant, Address, Tag)
│   │   ├── service/                # Domain services purs (logique métier sans I/O)
│   │   └── ports/                  # Interfaces (Entrées et Sorties mélangées à plat)
│   │       ├── SearchRestaurantsUseCase.java    # Port d'entrée appelé par le RestController
│   │       ├── RestaurantRepository.java        # Port sortant Postgres
│   │       └── RestaurantGraphRepository.java   # Port sortant Neo4J
│   │
│   ├── application/                # Services applicatifs (implémentations des use cases)
│   │   └── SearchRestaurantsService.java        # @Service Spring, orchestre les ports
│   │
│   └── adapters/                   # Implémentations techniques (Spring/Frameworks)
│       ├── rest/                   # Contrôleurs REST Spring, DTOs (Records), mappers
│       │   └── RestaurantRestController.java
│       ├── postgres/               # Entities JPA, Repositories PostGIS, mapper Entity↔Domain
│       │   └── PostgresRestaurantAdapter.java
│       └── neo4j/                  # Entities OGM, Cypher, mapper Node↔Domain
│           └── Neo4jRestaurantAdapter.java
│
├── review/                         # --- COMPOSANT MÉTIER : ÉVALUATIONS & NOTES ---
│   ├── domain/
│   │   ├── model/                  
│   │   └── ports/                  
│   │       ├── AddReviewUseCase.java
│   │       ├── ReviewRepository.java
│   │       └── ImageStoragePort.java
│   └── adapters/
│       ├── rest/                   
│       ├── postgres/               
│       ├── neo4j/                  
│       └── minio/                  # Implémentation de ImageStoragePort
│
├── user/                           # --- COMPOSANT MÉTIER : UTILISATEURS & ACCÈS ---
│   ├── domain/
│   │   ├── model/                  
│   │   └── ports/                  
│   │       ├── InviteUserUseCase.java
│   │       └── UserRepository.java
│   └── adapters/
│       ├── rest/                   
│       ├── postgres/               
│       └── keycloak/               # Implémentation des appels API Admin Keycloak
│
├── shared/                         # --- CONTEXTE PARTAGÉ (SHARED KERNEL) ---
│   ├── domain/
│   │   ├── exception/              # Exceptions métiers transverses (sealed)
│   │   └── valueobject/            # Value Objects génériques (UserId, Slug, Cursor...)
│   └── adapters/
│       ├── outbox/                 # Pattern Transactional Outbox (table + worker poll)
│       └── web/                    # ProblemDetail handler, pagination envelope, mapper curseur
│
└── config/                         # Configuration globale Spring
    ├── SecurityConfig.java         # Resource Server JWT + mapping rôles Keycloak
    └── ObservabilityConfig.java    # OpenTelemetry, Micrometer, MDC

**Règles d'isolement (vérifiées par ArchUnit, cf. §8) :**
- `domain/**` n'importe **rien** de `org.springframework.*`, `jakarta.*`, `org.neo4j.*`, `io.minio.*`. JDK standard uniquement.
- Aucune dépendance entre composants métiers (`restaurant` ne référence pas `review`). Communication transverse via événements outbox.
- Les adaptateurs ne se référencent jamais entre eux ; ils dépendent uniquement de leur `domain/` local.
- `application/**` ne dépend que de `domain/**` (jamais d'`adapters/**`).

6. Sécurité, Rôles & Gestion des Invitations

L'authentification et l'autorisation sont déléguées à Keycloak.  
6.1. Matrice des Rôles (RBAC)
Niveau	Rôle JWT	Endpoints Autorisés	Actions Permises
Lvl 1 : Anonyme	Aucun (Public)	/api/v1/public/**

Consultation des restaurants.  
Lvl 2 : Utilisateur	ROLE_USER	/api/v1/user/**

Notation, commentaires, édition de fiches, upload de photos.  
Lvl 3 : Admin	ROLE_ADMIN	/api/v1/admin/**

Validation des éditions, gestion des utilisateurs, génération d'invitations.  
6.2. Workflow d'Invitation Étanche

    Un ADMIN appelle POST /api/v1/admin/invitations.  

    Génération d'un token cryptographique unique stocké dans PostgreSQL (expiration 48h).  

    Le backend renvoie un lien unique : [https://palaisdivin.lepgu.fr/register?token=XYZ](https://palaisdivin.lepgu.fr/register?token=XYZ).  

    À la soumission, le backend valide le token, appelle l'API Admin de Keycloak pour créer l'utilisateur avec ROLE_USER, puis invalide le token d'invitation.

6.3. Gestion d'Erreurs — RFC 9457 (Problem Details for HTTP APIs)

Toutes les erreurs HTTP renvoient un `application/problem+json` via `ProblemDetail` (support natif Spring Framework 6+, étendu en Spring 7 / Boot 4). Pas de stack trace, pas de message Java brut côté client.

```json
{
  "type": "https://palaisdivin.lepgu.fr/problems/invitation-expired",
  "title": "Invitation expirée",
  "status": 410,
  "detail": "Le token d'invitation est expiré depuis 2026-05-24T18:12:00Z.",
  "instance": "/api/v1/public/register",
  "traceId": "0af7651916cd43dd8448eb211c80319c"
}
```

Les exceptions métier (sealed dans `shared/domain/exception`) sont mappées vers `ProblemDetail` par un `@RestControllerAdvice` global dans `shared/adapters/web/`. Les erreurs de validation (`@Valid` + Bean Validation) produisent un Problem Details avec extension `errors: [{field, code, message}]`.

7. Spécifications des Endpoints API Core (Exemples)
   7.1. Recherche et Filtrage de Restaurants

`GET /api/v1/public/restaurants`

**Query Params :**

| Param    | Type                          | Défaut | Description |
|----------|-------------------------------|--------|-------------|
| `lat`    | double (optionnel)            | —      | Latitude utilisateur (WGS84). |
| `lon`    | double (optionnel)            | —      | Longitude utilisateur. |
| `radius` | int (mètres, max 50 000)      | `5000` | Rayon de recherche. Ignoré si `lat/lon` absents. |
| `tags`   | array de strings (CSV)        | —      | Filtre AND (ex: `vegan,terrasse`). Whitelist serveur. |
| `sort`   | enum: `distance` \| `rating` \| `name` | `rating` si pas de `lat/lon`, sinon `distance` | Tri serveur exclusivement. |
| `cursor` | string Base64URL (optionnel)  | —      | Curseur opaque retourné par la page précédente. |
| `size`   | int (1..100)                  | `20`   | Taille de page. `400` si > 100. |

**Réponse `200 OK` :**

```json
{
  "data": [
    {
      "id": "r_abc-123",
      "name": "...",
      "distanceMeters": 842,
      "averageRating": 4.3,
      "tags": ["vegan", "terrasse"],
      "thumbnailUrl": "https://minio.../signed-url"
    }
  ],
  "page": {
    "size": 20,
    "hasNext": true,
    "nextCursor": "eyJrIjo4NDIsImlkIjoicl9hYmMtMTIzIiwidiI6MX0"
  }
}
```

Pour récupérer la page suivante, le client renvoie le `nextCursor` reçu, **sans** modifier `sort` ni les filtres (le curseur est lié au tri). Toute incohérence (`sort` changé, curseur d'un autre endpoint) → `400 Bad Request` avec `type: .../problems/invalid-cursor`.

7.2. Soumission d'une Note

`POST /api/v1/user/restaurants/{id}/reviews`

**Headers :** `Idempotency-Key: <uuid>` (recommandé — déduplication côté serveur sur fenêtre 24h pour éviter les doubles notations sur retry réseau).

**Payload :**
```json
{
  "rating": 4,
  "comment": "Excellente option vegan.",
  "tags": ["vegan", "terrasse"]
}
```

**Réponse `201 Created`** avec header `Location: /api/v1/user/restaurants/{id}/reviews/{reviewId}`.


---

## 8. Stratégie de Test Exceptionnelle (Production Grade)

### 8.1. Tests Unitaires (~70% du volume)
* **Périmètre** : Classes du package `domain/` (modèle, services purs) et `application/` (orchestration mockée).
* **Règle** : Aucun framework Spring, aucun conteneur. JUnit 5 + AssertJ + Mockito uniquement. Cible < 10ms par test, < 30s pour toute la couche.

### 8.2. Tests d'Architecture — ArchUnit (~5% du volume, gardiens)
* Valident les **règles d'isolement de §5** au build : zéro import Spring/Jakarta dans `domain/**`, zéro dépendance cross-composant, zéro adaptateur référençant un autre adaptateur.
* Exécutés dans la phase `test` Maven — un commit qui casse l'architecture fait échouer la CI immédiatement.

### 8.3. Tests d'Intégration avec Testcontainers (~25% du volume)
Pour valider les requêtes complexes (SQL PostGIS et Cypher Neo4J), la projection outbox, le flux Keycloak, et la signature S3 MinIO.
* **Technologie** : **Testcontainers Java** + **`@ServiceConnection`** (zéro `@DynamicPropertySource`).
* **Conteneurs éphémères** : `postgis/postgis:16-3.4`, `neo4j:5.20-enterprise`, `minio/minio`, `quay.io/keycloak/keycloak:latest`.
* **Optimisation** : **Shared Container** (singleton statique) + **réutilisation** Testcontainers (`testcontainers.reuse.enable=true` en local) pour rester sous 2 min sur le pipeline.
* **Synchronisation outbox** : assertions via `Awaitility` avec timeout 10s (jamais de `Thread.sleep`).

### 8.4. Tests de Contrat
* **Keycloak Admin API** : appel mocké en unitaire (HTTP Interface client), validé contre un vrai Keycloak en intégration. Toute évolution du schéma JSON Keycloak casse l'intégration avant la prod.

---

## 9. Intégration Continue (CI) & Déploiement

### 9.1. Pipeline de CI du Repository Backend
1.  **Lint / Style** : Checkstyle + Spotless (format Google Java Style, fail-fast).
2.  **Tests Unitaires + ArchUnit** : `mvn clean test` (échec immédiat sur violation d'architecture).
3.  **Tests d'Intégration** : `mvn verify -P integration-tests` (Testcontainers, shared container).
4.  **Scan sécurité** : OWASP Dependency-Check + Trivy sur l'image finale (échec sur CVE Critical).
5.  **Construction Docker** : Multi-stage build avec base **Distroless** Java 25 (`gcr.io/distroless/java25-debian12`). Image taggée par SHA + version sémantique.
6.  **Publication** : Push sur le registre privé.

> **Note GraalVM Native Image** : non retenu en v1 — Spring Data Neo4j + JPA ont un coût réflexion élevé et la marge de gain au cold-start ne justifie pas la complexité de configuration `reflect-config.json`. À réévaluer si l'app passe en autoscaling sévère.

### 9.2. Workflow de Déploiement (Repository Infra)
* Mise à jour des tags dans `docker-compose.yml`[cite: 1].
* Sur le VPS : `git pull` puis `docker compose up -d --remove-orphans`[cite: 1].
* **Caddy** gère le reverse proxy et les certificats TLS Let's Encrypt vers `palaisdivin.lepgu.fr`[cite: 1].

---

## 10. Bootstrap — Configuration Spring Initializr

Configuration exacte à sélectionner sur [start.spring.io](https://start.spring.io) pour scaffolder le projet conformément à ce document.

### 10.1. Métadonnées du projet

| Champ | Valeur |
|-------|--------|
| **Project** | Maven |
| **Language** | Java |
| **Spring Boot** | `4.0.6` (Spring Framework 7, Jakarta EE 11) |
| **Group** | `fr.lepgu` |
| **Artifact** | `palaisdivin-backend` |
| **Name** | `palaisdivin-backend` |
| **Description** | `Le Palais Divin — backend API (Web of Trust restaurant ratings)` |
| **Package name** | `fr.lepgu.palaisdivin.backend` |
| **Packaging** | Jar |
| **Java** | `25` |

### 10.2. Dependencies à cocher

**Web & sécurité**
- [x] **Spring Web** — REST controllers + Tomcat embarqué (compatible virtual threads).
- [x] **Spring Security** — base du Resource Server.
- [x] **OAuth2 Resource Server** — validation JWT Keycloak.
- [x] **Validation** — Bean Validation (Jakarta) pour DTOs et `@ConfigurationProperties`.

**Persistance**
- [x] **Spring Data JPA** — adaptateurs Postgres.
- [x] **PostgreSQL Driver** — JDBC officiel.
- [x] **Spring Data Neo4j** — adaptateurs graphe.
- [x] **Flyway Migration** — versionnage du schéma Postgres (PostGIS, outbox, etc.).

**Ops & dev**
- [x] **Spring Boot Actuator** — health, metrics, info (préfixe `/actuator`).
- [x] **Distributed Tracing** — Micrometer Tracing bridge (sélectionner la variante OpenTelemetry / OTLP).
- [x] **Docker Compose Support** — démarre Postgres/Neo4J/MinIO/Keycloak en `spring-boot:run`.
- [x] **Spring Boot DevTools** — reload, scope `developmentOnly` (jamais en prod).
- [x] **Spring Configuration Processor** — génère les metadata pour autocomplétion des `@ConfigurationProperties`.

**Test**
- [x] **Testcontainers** — l'option Initializr ajoute déjà JUnit Jupiter integration + module commun.

> ❌ **Ne pas cocher** : Lombok (records + `sealed` rendent Lombok inutile et masquent le pattern matching), Spring Data JDBC, Spring Web Reactive (incompatible avec le modèle blocking + virtual threads choisi ici).

### 10.3. Dépendances à ajouter manuellement au `pom.xml` (hors Initializr)

L'Initializr ne propose pas tout — compléter le `pom.xml` après génération avec :

```xml
<!-- MinIO client S3 -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.17</version>
</dependency>

<!-- Logback JSON encoder (logs structurés avec traceId/spanId) -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>

<!-- ArchUnit (tests d'architecture, cf. §8.2) -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.4.0</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility (assertions sur projection outbox asynchrone) -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers : modules spécifiques (Initializr n'ajoute que le module commun) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>neo4j</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>minio</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.6.0</version>
    <scope>test</scope>
</dependency>
```

> **Note Keycloak Admin API** : ne pas ajouter `keycloak-admin-client` officiel — préférer un **HTTP Interface Client** déclaratif (`@HttpExchange`) sur la base du `RestClient` Spring, comme spécifié en §4.2. Cela évite une dépendance lourde et garde le contrôle des timeouts directement sur le `RestClient.Builder`.

### 10.4. Profil Maven `integration-tests`

À ajouter dans le `pom.xml` après scaffolding pour exécuter les tests d'intégration séparément (cf. §9.1) :

```xml
<profiles>
    <profile>
        <id>integration-tests</id>
        <build>
            <plugins>
                <plugin>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*IT.java</include>
                        </includes>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Convention : classes `*Test.java` exécutées par Surefire en phase `test`, classes `*IT.java` réservées au profil `integration-tests`.

---

## 11. Développement Local

`mvn verify -P integration-tests` (et tout test `*IT.java`) démarre des conteneurs Testcontainers — PostGIS, Neo4j, MinIO, Keycloak — et requiert donc un **démon Docker actif** sur la machine de dev. Lancer **Docker Desktop**, **OrbStack** (macOS, recommandé pour sa légèreté), ou **Colima** avant de jouer la commande. Symptôme caractéristique d'un démon absent : `Could not find a valid Docker environment`. La phase `mvn test` (unitaires + ArchUnit) ne dépend pas de Docker et tourne sans aucun conteneur.