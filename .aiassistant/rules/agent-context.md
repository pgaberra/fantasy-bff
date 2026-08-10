---
apply: always
---

# Fantasy Hockey BFF — AI Agent Context Document

## Purpose of This Document

This document provides everything an AI agent needs to understand, reason about, and contribute to the **Fantasy Hockey Backend for Frontend (BFF)**. It covers the system's role in the overall architecture, coding conventions, integration patterns, security model, and testing strategy. Always consult this document before generating code, suggesting architectural changes, or designing new endpoints.

---

## 1. Architecture Overview

### What Is This Project?

This is a **Backend for Frontend (BFF)** service built with **Java 25 and Spring Boot 4**. Its sole purpose is to act as an orchestration and aggregation layer between the **Angular frontend** and a set of downstream **microservices** that the team will build over time.

The BFF does **not** contain business logic or own any data. It:

- Receives requests from the Angular frontend
- Fans out to one or more downstream microservices via REST/HTTP
- Aggregates, transforms, and shapes the responses into frontend-friendly payloads
- Handles authentication and passes identity context downstream
- Returns a single, clean response to the frontend

### System Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Angular Frontend                     │
└────────────────────────┬────────────────────────────────┘
                         │ HTTPS (JWT in Authorization header)
                         ▼
┌─────────────────────────────────────────────────────────┐
│              Fantasy Hockey BFF (This Project)          │
│                   Java Spring Boot                      │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Controllers│  │  Services    │  │  HTTP Clients │  │
│  │  (Routes)   │→ │  (Orchestr.) │→ │  (RestClient) │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
└────────────────────┬──────────┬──────────┬──────────────┘
                     │          │           │   REST/HTTP (OpenAPI)
          ┌──────────┘  ┌───────┘   ┌──────┘
          ▼             ▼           ▼
   ┌─────────────┐ ┌──────────┐ ┌──────────────┐ ┌────────────┐
   │  Yahoo      │ │  ESPN    │ │  Database    │ │ Projection │
   │  Service    │ │  Service │ │  Service     │ │  Service   │
   │ (players +  │ │ (leagues │ │ (users +     │ │  (model    │
   │  leagues)   │ │  + stats)│ │  projections)│ │   output)  │
   └─────────────┘ └──────────┘ └──────────────┘ └────────────┘
```

### Key Architectural Principles

- **No business logic in the BFF.** If a rule belongs to a domain (e.g. roster eligibility, scoring rules), it lives in the relevant downstream service.
- **Frontend-driven API design.** Endpoints and response shapes are designed around what the Angular app needs, not around what downstream services return.
- **OpenAPI-first for downstream communication.** Each downstream microservice exposes an OpenAPI spec. The BFF generates typed HTTP clients from these specs.
- **Stateless.** The BFF holds no session state. All identity is carried via JWT.
- **Virtual threads over reactive.** The BFF uses Java Virtual Threads (Project Loom) for concurrency instead of WebFlux/reactive streams. This keeps the code synchronous and readable, avoids the all-or-nothing reactive stack constraint, and allows freely mixing blocking operations (e.g. future JDBC calls) without performance penalties.
- **Fail gracefully.** Partial failures from downstream services should not crash the entire response — see the Error Handling section.

---

## 2. Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.x |
| Concurrency | Virtual Threads (Project Loom) — enabled via `spring.threads.virtual.enabled=true` |
| HTTP Client | Spring `RestClient` (synchronous, virtual-thread-friendly) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Auth | JWT (Spring Security) |
| Build Tool | Gradle (Groovy DSL) |
| Testing | JUnit 5, Mockito, WireMock, Spring Boot Test |
| Serialization | Jackson |

---

## 3. Project Structure

```
src/
├── main/
│   └── java/com/fantasyhockey/bff/
│       ├── BffApplication.java
│       ├── config/
│       │   ├── SecurityConfig.java          # JWT filter chain
│       │   ├── RestClientConfig.java        # Downstream HTTP clients
│       │   └── OpenApiConfig.java           # Swagger/OpenAPI setup
│       ├── controller/
│       │   ├── PlayerController.java
│       │   ├── TeamController.java
│       │   ├── LeagueController.java
│       │   └── ...
│       ├── service/
│       │   ├── PlayerService.java           # Orchestration logic
│       │   ├── TeamService.java
│       │   └── ...
│       ├── client/
│       │   ├── PlayerServiceClient.java     # Typed downstream client
│       │   ├── YahooServiceClient.java
│       │   ├── DatabaseServiceClient.java
│       │   └── ...
│       ├── dto/
│       │   ├── request/                     # Inbound from frontend
│       │   └── response/                    # Outbound to frontend
│       ├── model/
│       │   └── downstream/                  # Models mapped from downstream APIs
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       └── security/
│           ├── JwtAuthenticationFilter.java
│           └── JwtTokenValidator.java
└── test/
    └── java/com/fantasyhockey/bff/
        ├── controller/
        ├── service/
        └── client/
```

---

## 4. API Endpoints & Routes

### Conventions

- All routes are prefixed with `/api/v1/`
- Request and response bodies are JSON
- All endpoints (except `/api/v1/auth/**` and `/actuator/health`) require a valid JWT in the `Authorization: Bearer <token>` header

### The downstream services

All four exist and are deployed. There is no mocking strategy and no `mock` profile: tests
replace the client interfaces with `@MockitoBean`, and clients are exercised against WireMock
or `MockRestServiceServer`.

| Service | Responsibility |
|---|---|
| `fantasy-db-service` | Users, accounts and saved projections (Postgres). |
| `fantasy-yahoo-service` | Per-user Yahoo OAuth, league settings, **and the cached player read model** — identity, eligible positions and season stats for every player the app serves. |
| `fantasy-espn-service` | ESPN leagues (no OAuth — public leagues need an id, private ones the user's cookies) and cached ESPN stat lines for the stats Yahoo does not report. |
| `fantasy-projection-service` | The projection model's own output, keyed by NHL id. |

A former `fantasy-nhl-service` and `fantasy-player-service` were **retired** once all player
data came from Yahoo. Nothing in this repo talks to the NHL API.

---

### Confirmed Endpoints

These are the endpoints currently known and required by the Angular frontend. This list will grow as the frontend evolves.

#### Authentication

| Method | Path | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | ❌ | Register a new user account |
| `POST` | `/api/v1/auth/login` | ❌ | Log in and receive a JWT |

**Register request body:**
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

**Login request body:**
```json
{
  "email": "string",
  "password": "string"
}
```

**Login response:**
```json
{
  "token": "string",
  "expiresIn": 900
}
```

#### Players

The Angular `PlayerService` currently provides two data streams that will be replaced by BFF calls: `getSkaters()` and `getGoalies()`. The BFF exposes these as two separate endpoints, matching the frontend's separation of skaters and goalies.

| Method | Path | Auth Required | Description | Downstream Service |
|---|---|---|---|---|
| `GET` | `/api/v1/players/skaters` | ❌ public | Every skater with full stats | `yahoo-service` (read model) + `espn-service` (the stats Yahoo lacks) |
| `GET` | `/api/v1/players/goalies` | ❌ public | Every goalie with full stats | same |

**Skater response shape** (array of skaters):
```json
{
  "type": "skater",
  "id": 1,
  "name": "Connor McDavid",
  "positions": ["C"],
  "stats": {
    "utility": {
      "gp": 82,
      "toiPerGame": 1320
    },
    "scoring": {
      "goals": 64, "assists": 89, "plusMinus": 33, "pim": 36,
      "ppg": 22, "ppa": 38, "shg": 1, "sha": 0, "gwg": 8,
      "sog": 348, "shPct": 18.4, "fw": 812, "fl": 623,
      "hits": 42, "blocks": 28
    }
  }
}
```

**Goalie response shape:**
```json
{
  "type": "goalie",
  "id": 101,
  "name": "Igor Shesterkin",
  "stats": {
    "utility": { "gp": 58 },
    "scoring": {
      "gs": 58, "w": 36, "l": 17, "sho": 3,
      "sa": 1720, "sv": 1565, "ga": 155,
      "gaa": 2.67, "svPct": 0.910
    }
  }
}
```

> **Note for agent:** The `positions` field is a `Set<String>` in the Angular model. Serialize it as a JSON array in the BFF response. A player may have multiple positions (e.g. `["C", "LW"]`).

---

### Future Endpoints

The following endpoint groups are anticipated based on the application's domain but are **not yet confirmed or designed**. Do not implement these until the frontend requirements are defined. Add them to this table as they are scoped.

| Endpoint Group | Status |
|---|---|
| Fantasy league management (standings, matchups, rosters) | 🔲 Not scoped |
| NHL team data | 🔲 Not scoped |
| User profile | 🔲 Not scoped |

> **Note for agent:** When a new endpoint is confirmed, move it from this table into the Confirmed Endpoints section above with full request/response documentation before writing any code.

---

## 5. Authentication & Security

### Strategy: JWT (Stateless)

The BFF uses **JWT (JSON Web Tokens)** for authenticating requests from the Angular frontend. This is a stateless approach — no server-side sessions are stored.

#### Recommendation on JWT vs. OAuth 2.0

Since downstream services (Yahoo Fantasy) will eventually require OAuth 2.0, consider this split:

- **BFF ↔ Frontend:** JWT issued by the BFF after the user logs in. The Angular app stores this token and sends it in every request.
- **BFF ↔ Yahoo Service:** The Yahoo microservice handles the OAuth 2.0 flow with Yahoo's API. The BFF does not deal with OAuth directly — it simply calls the Yahoo service, which manages its own token lifecycle.

This keeps the BFF clean and the Angular app simple.

#### JWT Flow

```
1. User logs in via POST /api/v1/auth/login (username + password)
2. BFF validates credentials (via Database Service)
3. BFF issues a signed JWT (HS256 or RS256) with claims:
   - sub: userId
   - email: user email
   - roles: [USER, ADMIN, ...]
   - iat / exp: issued at / expiry
4. Angular stores token (preferably in memory or httpOnly cookie)
5. Angular sends: Authorization: Bearer <token> on every request
6. BFF's JwtAuthenticationFilter validates the token on every request
7. If valid, SecurityContext is populated and the request proceeds
```

#### Spring Security Configuration

- Use `SecurityFilterChain` (not the deprecated `WebSecurityConfigurerAdapter`)
- Permit: `/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`
- Require authentication on all other routes
- Propagate user identity downstream by forwarding a stripped or internal JWT in service-to-service calls (or pass `X-User-Id` / `X-User-Roles` headers — to be decided)

#### Security Checklist

- [ ] Tokens are signed (prefer RS256 in production for key rotation support)
- [ ] Token expiry is enforced (recommended: 15 min access token, 7 day refresh token)
- [ ] CORS is configured to only allow the Angular frontend origin
- [ ] HTTPS is enforced in all non-local environments
- [ ] Sensitive config (JWT secret, service URLs) is stored in environment variables, never in code

---

## 6. External API Integrations (Downstream Microservices)

### Concurrency Model: Virtual Threads

This project uses **Java Virtual Threads (Project Loom)** instead of reactive/non-blocking I/O (WebFlux). This is an intentional architectural decision.

**Why not WebFlux?** WebFlux requires the entire stack to be non-blocking. Any blocking call (e.g. a synchronous library, a future JDBC driver, a third-party SDK) risks starving the carrier thread pool and causes subtle, hard-to-diagnose performance problems. It also forces a reactive programming model (`Mono`/`Flux`) throughout the codebase, adding significant complexity.

**Why Virtual Threads?** Virtual threads are extremely cheap to create and block. The JVM schedules them onto OS threads automatically, meaning blocking I/O (like HTTP calls to downstream services) no longer ties up a thread in any meaningful way. This gives near-equivalent throughput to reactive code while keeping the programming model simple, synchronous, and imperative. It also means there are **no constraints on what the rest of the stack can do** — JDBC, blocking libraries, future integrations all work without special handling.

Enable virtual threads in `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### Integration Pattern

The BFF communicates with all downstream services via **REST/HTTP using OpenAPI contracts**. Each downstream service:

1. Publishes an OpenAPI 3.x spec (`openapi.yaml` or `/v3/api-docs`)
2. The BFF uses Spring **`RestClient`** (synchronous, introduced in Spring 6.1) to call these services
3. Client classes live in `com.fantasyhockey.bff.client`
4. `RestClient` calls block the virtual thread — this is intentional and safe under Project Loom

### RestClient Setup

Each downstream service gets its own `RestClient` bean, configured with:
- Base URL (from application properties)
- Default headers (e.g. internal auth token or `X-Service-Id`)
- Timeout settings (connect timeout, read timeout)
- Error handling via `onStatus()`

```java
// Example: RestClientConfig.java
@Bean
public RestClient yahooFantasyServiceClient(
        @Value("${services.yahoo-fantasy.base-url}") String baseUrl,
        @Value("${services.yahoo-fantasy.timeout-ms}") int timeoutMs) {

    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build();

    ClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(httpClient);

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
}
```

### Adding a downstream service

Create a `{Name}ServiceClient` interface plus an `Http{Name}ServiceClient` in `client/`, pin
the service's spec in `specs/`, add a generate task in `build.gradle`, register a `RestClient`
bean in `RestClientConfig`, and give `SPEC_READ_TOKEN` read access to the new repo so the
drift check can reach it. See the espn-service wiring for a recent worked example.

### Application Configuration

```yaml
# application.yaml — one base-url / timeout / api-key per downstream
services:
  yahoo-fantasy:
    base-url: ${YAHOO_SERVICE_URL:http://localhost:8088}
    timeout-ms: 10000
    api-key: ${YAHOO_INTERNAL_API_KEY:}
  espn-fantasy:
    base-url: ${ESPN_SERVICE_URL:http://localhost:8090}
    timeout-ms: 10000
    api-key: ${ESPN_INTERNAL_API_KEY:}
  database:
    base-url: ${DATABASE_SERVICE_URL:http://localhost:8086}
    timeout-ms: 3000
    api-key: ${DB_INTERNAL_API_KEY:}
  projection:
    base-url: ${PROJECTION_SERVICE_URL:http://localhost:8092}
    timeout-ms: 5000
    api-key: ${PROJECTION_INTERNAL_API_KEY:}

security:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 900000       # 15 minutes
    refresh-expiration-ms: 604800000  # 7 days
```

---

## 7. Data Models

### Key DTOs

#### PlayerProfileResponse
```java
public record PlayerProfileResponse(
    String playerId,
    String firstName,
    String lastName,
    String position,
    String nhlTeam,
    PlayerStatsDto currentSeasonStats,
    FantasyOwnershipDto fantasyOwnership
) {}
```

#### FantasyLeagueResponse
```java
public record FantasyLeagueResponse(
    String leagueId,
    String leagueName,
    int season,
    List<FantasyTeamSummaryDto> teams,
    String currentMatchupPeriod
) {}
```

> **Note for agent:** All DTOs should be Java `record` types (immutable). Request DTOs live in `dto/request/`, response DTOs in `dto/response/`. Internal models mapped from downstream service responses live in `model/downstream/`.

---

## 8. Error Handling

### Philosophy

- **Prefer standard Java exceptions over custom ones.** Instead of creating custom exception classes, use built-in Java exceptions that semantically match the error condition.
- The BFF should **never return a 500** due to a downstream service being unavailable if a graceful fallback is possible.
- Use **partial responses** when feasible: `/players` serves the Yahoo line without the ESPN-only stats when espn-service is unreachable, rather than failing the request.
- All unhandled exceptions are caught by `GlobalExceptionHandler`.

### Global Exception Handler

Use `@RestControllerAdvice` with `@ExceptionHandler` methods that map each exception type to an appropriate HTTP status and a consistent `ErrorDto`.

| Exception | HTTP Status | Use Case |
|---|---|---|
| `SecurityException` | `401 Unauthorized` | JWT invalid/expired, invalid credentials |
| `NoSuchElementException` | `404 Not Found` | Requested resource doesn't exist |
| `IllegalArgumentException` | `400 Bad Request` | Invalid input, business rule violation |
| `IllegalStateException` | `502 Bad Gateway` | Downstream service error or timeout |
| `MethodArgumentNotValidException` | `400 Bad Request` | Jakarta Validation failure |
| `Exception` (catch-all) | `500 Internal Error` | Unexpected system failure |

### Downstream Resilience

- Set **timeouts** on all `RestClient` calls (connect + read timeout via `JdkClientHttpRequestFactory`)
- Use **try/catch** around `RestClient` calls to handle downstream failures and map them to `IllegalStateException` (502 Bad Gateway)
- Use **`onStatus()`** on `RestClient` calls to treat non-2xx responses as exceptions
- Consider adding **Resilience4j** for circuit breaking and retry logic as the system matures

---

## 9. Testing Strategy

### Layers & Tools

| Layer | Tool | What to Test |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service orchestration logic, DTO mapping, JWT validation |
| Integration | Spring Boot Test + WireMock | Controller → Service → Client flow with mocked downstream HTTP |
| Contract | OpenAPI validation | BFF response shapes match the OpenAPI spec served to the frontend |
| Security | Spring Security Test | Protected routes reject unauthenticated/unauthorized requests |

### Unit Tests (Services)

- Mock all `*Client` dependencies with Mockito
- Test aggregation logic: what happens when downstream A returns X and downstream B returns Y?
- Test failure cases: what does the service return when a downstream call fails?

### Integration Tests (Controllers)

- Use `@SpringBootTest` with `@AutoConfigureMockMvc`
- Use **WireMock** to stub all downstream service HTTP calls
- Test the full request/response cycle including headers, status codes, and response body shape
- Test auth: requests without a JWT return `401`, requests with an invalid JWT return `401`

### Example Test Structure

```
src/test/java/com/fantasyhockey/bff/
├── controller/
│   ├── PlayerControllerIntegrationTest.java
│   └── LeagueControllerIntegrationTest.java
├── service/
│   ├── PlayerServiceTest.java
│   └── LeagueServiceTest.java
├── client/
│   └── HttpEspnServiceClientTest.java
└── security/
    └── JwtAuthenticationFilterTest.java
```

### Coverage Target

Aim for **≥ 80% line coverage** on `service` and `controller` packages. Client classes are covered by WireMock integration tests.

---

## 10. Development Guidelines for the AI Agent

When generating code or making suggestions for this project, always follow these rules:

1. **Use Java records for DTOs** — immutable, concise, and idiomatic for Spring Boot 4+.
2. **Use `RestClient`, not `WebClient` or `RestTemplate`** — `RestTemplate` is deprecated; `WebClient` is not used in this project. `RestClient` is the correct synchronous client.
3. **Never write reactive code (`Mono`, `Flux`, `.subscribe()`)** — the concurrency model is virtual threads + synchronous code. Reactive patterns are explicitly avoided in this project.
4. **Never hardcode URLs or secrets** — always use `@Value` or `@ConfigurationProperties` backed by `application.yml` and environment variables.
5. **Follow the layered structure** — Controllers call Services, Services call Clients. No cross-layer skipping.
6. **Design endpoints for the frontend, not the backend** — the Angular app's needs drive the response shape.
7. **Add OpenAPI annotations** — annotate all controllers with `@Operation`, `@ApiResponse`, and `@Tag` so the Swagger UI stays accurate.
8. **Write a test for every new service method** — unit test for happy path and at least one failure/edge case.
9. **Do not add new downstream services without updating** this document's downstream services table and `RestClientConfig.java`.
10. **Avoid returning `null` in Java** — Always use `java.util.Optional` for methods that may not return a value. This improves code readability and forces explicit handling of the absent case.
11. **Always mark DTO fields as required in OpenAPI** — Annotate every field in every DTO record (both request and response) with `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)`. Without this, springdoc-openapi marks all fields as optional, which causes `ng-openapi-gen` to generate TypeScript interfaces where every field is nullable (`?`). Fields should be required by default; only explicitly omit the annotation (leaving a field optional) in the rare case where the field is genuinely optional in the API contract. Collection fields (e.g. `List`, `Set`) must never be nullable — always annotate them as required.
