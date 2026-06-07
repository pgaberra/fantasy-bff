# CLAUDE.md — fantasy-bff

Backend-for-Frontend (BFF) for the fantasy hockey draft tool. It is the only
service the Angular frontend (`fantasy-web`) talks to. It handles auth (JWT),
serves player data, and orchestrates calls to downstream services
(`fantasy-db-service` for users/persistence, an NHL data service for stats).

@.aiassistant/rules/agent-context.md

## Tech stack

- Java 25, Spring Boot 4.0.5, Gradle (wrapper: `./gradlew`)
- Spring Security + JWT (jjwt 0.12.6, HS256)
- Spring WebMVC (virtual threads enabled), `RestClient` for downstream calls
- springdoc OpenAPI / Swagger UI
- openapi-generator 7.13.0 (generates model POJOs from `specs/` at compile time)
- Tests: JUnit 5, Spring Boot Test, MockMvc, WireMock (standalone), H2 not used here

## Common commands

```bash
./gradlew build          # compile + test (CI runs: ./gradlew build --no-daemon)
./gradlew test           # tests only
./gradlew bootRun        # run locally (defaults to no profile — usually run with mock)
SPRING_PROFILES_ACTIVE=mock ./gradlew bootRun
./gradlew openApiGenerate  # regenerate models from specs/ (runs automatically on build)
```

Swagger UI (when running): `http://localhost:8080/swagger-ui.html`

**Updating db-service client models:** when `fantasy-db-service` adds or changes an
endpoint, update `specs/fantasy-db-service-openapi.yaml` to match, then run
`./gradlew openApiGenerate`. Generated classes land in
`com.fantasy.bff.generated.db.model` (not committed — regenerated on every build).

## Architecture

- `controller/` — REST endpoints under `/api/v1` (`AuthController`, `PlayerController`)
- `service/` — business logic (`AuthService`, `PlayerService`)
- `client/` — downstream clients. Each has an **interface** plus a **mock**
  (`@Profile("mock")`) and an **http** (`@Profile("!mock")`) implementation.
  - `NhlServiceClient` — only `MockNhlServiceClient` exists today. A real HTTP
    impl is still TODO, so the app cannot run fully outside the `mock` profile yet.
  - `DatabaseServiceClient` — `MockDatabaseServiceClient` + `HttpDatabaseServiceClient`
    (talks to `fantasy-db-service`).
- `config/` — `SecurityConfig`, `RestClientConfig` (downstream `RestClient` beans),
  `*Properties` (typed config), `OpenApiConfig`
- `security/` — `JwtAuthenticationFilter`, `JwtTokenValidator`
- `dto/` — request/response records
- `model/downstream/` — models for downstream responses (e.g. `User`)
- `exception/GlobalExceptionHandler` — maps exceptions → `ErrorDto`

## Profiles & config (`src/main/resources/application*.yaml`)

- **base** (`application.yaml`): downstream service URLs (env-overridable),
  JWT settings, `server.port=${PORT:8080}`, permitted URLs.
- **`mock`**: swaps in mock downstream clients + a mock user
  (`mock@example.com` / `mockpassword123`). Use this for local dev and tests.
- **`dev`**: permits Swagger + CORS from `http://localhost:4200`.
- **`staging`**: CORS via `${WEB_ORIGIN}`; used on Render.

`JWT_SECRET` must be ≥32 chars (HS256). On staging it is a Render `generateValue`.

## Conventions

- Downstream clients are always interface + mock + http split by profile.
- Tests run under `@ActiveProfiles("mock")` (often `{"mock","dev"}`); they never
  hit real downstream services — http clients are tested in isolation with WireMock.
- Keep new endpoints under `/api/v1`. Add the path to `security.permitted-urls`
  only if it should be public (default is authenticated).

### OpenAPI-first downstream clients

All BFF → downstream service communication must use **OpenAPI-generated** typed
clients, not hand-written `RestClient` calls.

Workflow for a new downstream service:
1. Ensure the downstream service has complete `@Operation`, `@ApiResponse`, and
   `@Schema` annotations on its controllers and DTOs.
2. Add a committed spec YAML to `specs/` and a new `openApiGenerate`-style task in
   `build.gradle` (or extend the existing one with multiple inputs).
3. Keep the existing `interface` + `@Profile("mock")` / `@Profile("!mock")` split;
   use the generated model classes in the `!mock` implementation.

The db-service client is now fully generated — `HttpDatabaseServiceClient` uses
models from `com.fantasy.bff.generated.db.model` derived from
`specs/fantasy-db-service-openapi.yaml`.

`specs/fantasy-db-service-openapi.yaml` is a **verbatim pinned copy** of
`fantasy-db-service`'s `specs/openapi.yaml`. CI fails if it drifts from that repo's
`master` (see below). To update after a db-service API change: copy the new
`specs/openapi.yaml` over the pinned copy, run `./gradlew openApiGenerate`, and fix
any resulting compile errors.

### Own spec snapshot (`specs/bff-openapi.yaml`)

The BFF is itself a producer — `fantasy-web` generates its client from this spec.
`OpenApiSpecSnapshotTest` boots the app (mock profile) and asserts
`specs/bff-openapi.yaml` matches the live `/v3/api-docs.yaml`, so **any
controller/DTO change that isn't reflected in the spec fails the build**.

After an intentional API change, regenerate and commit:
```
./gradlew test -DupdateSpec=true   # rewrites specs/bff-openapi.yaml
git add specs/bff-openapi.yaml
```
`OpenApiConfig` pins the server URL to `/` so the spec is deterministic across runs.

## CI / workflow

- `.github/workflows/pr-checks.yml`: runs `./gradlew build --no-daemon` on PRs to `master`.
- A **spec drift check** runs first: it fetches `fantasy-db-service`'s spec from
  `master` and fails if the pinned copy differs. This needs a repo secret
  `SPEC_READ_TOKEN` — a fine-grained PAT with read access to `fantasy-db-service`
  contents.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

See root `CLAUDE.md` for the PR merge convention and commit message rules.

## Deployment

- Dockerized (multi-stage `Dockerfile`), deployed to Render as a web service
  (`SPRING_PROFILES_ACTIVE=mock,staging`). See `DEPLOYMENT.md`.
- Health check: `/actuator/health`.
