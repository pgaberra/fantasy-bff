# CLAUDE.md — fantasy-bff

Backend-for-Frontend (BFF) for the fantasy hockey draft tool. It is the only
service the Angular frontend (`fantasy-web`) talks to. It handles auth (JWT),
serves player data, and orchestrates calls to downstream services
(`fantasy-db-service` for users/persistence, an NHL data service for stats).

## Tech stack

- Java 25, Spring Boot 4.0.5, Gradle (wrapper: `./gradlew`)
- Spring Security + JWT (jjwt 0.12.6, HS256)
- Spring WebMVC (virtual threads enabled), `RestClient` for downstream calls
- springdoc OpenAPI / Swagger UI
- Tests: JUnit 5, Spring Boot Test, MockMvc, WireMock (standalone), H2 not used here

## Common commands

```bash
./gradlew build          # compile + test (CI runs: ./gradlew build --no-daemon)
./gradlew test           # tests only
./gradlew bootRun        # run locally (defaults to no profile — usually run with mock)
SPRING_PROFILES_ACTIVE=mock ./gradlew bootRun
```

Swagger UI (when running): `http://localhost:8080/swagger-ui.html`

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

## CI / workflow

- `.github/workflows/pr-checks.yml`: runs `./gradlew build --no-daemon` on PRs to `master`.
- Branch → push → PR → checks pass → **squash merge** to `master`.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

### Merging PRs

GitHub squash merge uses the **PR title** as the commit message — the individual
branch commits are ignored. Before merging:

1. Ensure the PR title is a proper commit message (e.g. `feat: add X`, `fix: correct Y`).
   Rename it first with `gh pr edit <n> --title "..."` if needed.
2. Merge with an explicit subject so the commit message is never left to chance:
   ```
   gh pr merge <n> --squash --delete-branch \
     --subject "feat: describe the change (#<n>)" \
     --body "Optional longer description."
   ```

Never merge a PR titled "wip", "draft", or similar.

## Deployment

- Dockerized (multi-stage `Dockerfile`), deployed to Render as a web service
  (`SPRING_PROFILES_ACTIVE=mock,staging`). See `DEPLOYMENT.md`.
- Health check: `/actuator/health`.

## Commit messages

End commit messages with:

```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
