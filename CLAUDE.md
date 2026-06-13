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

# Run locally (start Postgres + db-service + nhl-service first — see those repos):
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=<32chars> ./gradlew bootRun

./gradlew openApiGenerate     # regenerate db-service models from specs/
./gradlew generateNhlClient   # regenerate nhl-service models from specs/
```

Swagger UI (when running): `http://localhost:8080/swagger-ui.html`

**Updating db-service client models:** when `fantasy-db-service` adds or changes an
endpoint, update `specs/fantasy-db-service-openapi.yaml` to match, then run
`./gradlew openApiGenerate`. Generated classes land in
`com.fantasy.bff.generated.db.model` (not committed — regenerated on every build).

## Architecture

- `controller/` — REST endpoints under `/api/v1` (`AuthController`, `PlayerController`,
  `ProjectionController` — the user's saved projections; takes the user id from the JWT
  and forwards to db-service, never trusting a client-supplied user id)
- `service/` — business logic (`AuthService`, `PlayerService`)
- `client/` — downstream clients. Each is an **interface** plus an **http**
  implementation that uses OpenAPI-generated models (no mock implementations —
  tests replace clients with `@MockitoBean`).
  - `NhlServiceClient` — `HttpNhlServiceClient` talks to `fantasy-nhl-service` and
    owns the NHL-native → frontend-shape mapping (position codes, `avgToi` → seconds,
    `shootingPctg` fraction → percent, ppa/sha derived from points − goals,
    null stats → zeroed blocks for rookies).
  - `DatabaseServiceClient` — `HttpDatabaseServiceClient` talks to `fantasy-db-service`.
- `config/` — `SecurityConfig`, `RestClientConfig` (downstream `RestClient` beans),
  `*Properties` (typed config), `OpenApiConfig`
- `security/` — `JwtAuthenticationFilter`, `JwtTokenValidator`, and
  `GoogleTokenVerifier`/`NimbusGoogleTokenVerifier` (validates Google ID tokens against
  Google's JWKS: signature, issuer, audience = `security.google.client-id`, verified
  email). `POST /api/v1/auth/google` exchanges a Google ID token for our own JWT pair;
  db-service resolves the account (find by subject / link by email / create
  password-less). The Client ID is **public** (shipped to the browser by design) and
  comes from `${GOOGLE_CLIENT_ID}`; when unset the endpoint rejects all requests.
- `dto/` — request/response records
- `model/downstream/` — models for downstream responses (e.g. `User`)
- `exception/GlobalExceptionHandler` — maps exceptions → `ErrorDto`

## Profiles & config (`src/main/resources/application*.yaml`)

- **base** (`application.yaml`): downstream service URLs (env-overridable),
  JWT settings, `server.port=${PORT:8080}`, permitted URLs,
  `services.nhl.season` (NHL season id the projections are based on).
- **`dev`**: permits Swagger + CORS from `http://localhost:4200`.
- **`staging`**: the **deployed** profile (used by both prod and staging on Coolify);
  permits Swagger and adds CORS via `${WEB_ORIGIN}`. No downstream timeout overrides —
  the services are co-located on the Docker network, so the base timeouts apply.

`JWT_SECRET` must be ≥32 chars (HS256) and is supplied per environment as a Coolify env var.

## Conventions

- Downstream clients are an interface + an http implementation. Tests never hit
  real downstream services: integration tests replace the client interfaces with
  `@MockitoBean`; http clients are tested in isolation with WireMock.
- Keep new endpoints under `/api/v1`. Add the path to `security.permitted-urls`
  only if it should be public (default is authenticated).

### Logging & error handling

**Never silence an error.** `GlobalExceptionHandler` has a catch-all
`@ExceptionHandler(Exception.class)` that **logs the full stack trace** (`log.error`)
and returns a consistent `ErrorDto` — an unmatched exception must never surface as an
opaque 500 with no server-side trace (a downstream failure was once undiagnosable
because of exactly this). Rules of thumb:

- **5xx / genuine faults** (unexpected exceptions, a downstream service returning a
  non-2xx or being unreachable — see the `RestClientException` handler): log at `ERROR`
  with the exception so the stack trace and upstream status are captured.
- **4xx / expected client outcomes** (unauthorized, bad request, validation): do **not**
  log as errors — they are normal and would just be noise.

The same convention is documented in `fantasy-db-service` and `fantasy-nhl-service`.

### OpenAPI-first downstream clients

All BFF → downstream service communication must use **OpenAPI-generated** typed
clients, not hand-written `RestClient` calls.

Workflow for a new downstream service:
1. Ensure the downstream service has complete `@Operation`, `@ApiResponse`, and
   `@Schema` annotations on its controllers and DTOs.
2. Add a committed spec YAML to `specs/` and a new `openApiGenerate`-style task in
   `build.gradle` (see `generateNhlClient` for the pattern).
3. Define an `interface` in `client/` and implement it with the generated model
   classes; integration tests mock the interface with `@MockitoBean`.

Both downstream clients are fully generated: `HttpDatabaseServiceClient` uses
`com.fantasy.bff.generated.db.model` (from `specs/fantasy-db-service-openapi.yaml`)
and `HttpNhlServiceClient` uses `com.fantasy.bff.generated.nhl.model` (from
`specs/fantasy-nhl-service-openapi.yaml`).

The `specs/fantasy-*-openapi.yaml` files are **verbatim pinned copies** of each
service's `specs/openapi.yaml`. CI fails if they drift from the respective repo's
`master` (see below). To update after a downstream API change: copy the new
`specs/openapi.yaml` over the pinned copy, re-run the generate task, and fix any
resulting compile errors.

### Own spec snapshot (`specs/bff-openapi.yaml`)

The BFF is itself a producer — `fantasy-web` generates its client from this spec.
`OpenApiSpecSnapshotTest` boots the app and asserts
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
- A **spec drift check** runs first: it fetches each downstream service's spec from
  its `master` and fails if the pinned copy differs. This needs a repo secret
  `SPEC_READ_TOKEN` — a fine-grained PAT with read access to `fantasy-db-service`
  and `fantasy-nhl-service` contents.
- `@claude` mentions on issues/PRs trigger `.github/workflows/claude.yml`.

## Monorepo conventions

Shared across all four repos (`fantasy-web` → `fantasy-bff` → `fantasy-db-service` +
`fantasy-nhl-service`). The web talks only to the BFF; inter-service calls to db/nhl use a
shared `X-Internal-Api-Key` header.

### Input validation

**Every service validates its own inbound data independently** — never trust that an
upstream caller (e.g. the BFF) validated correctly. Reject malformed input at the
boundary with Bean Validation (`@Valid` on the controller param + `@NotBlank` / `@Email`
/ `@Size` / … on the DTO). **Every user-supplied string gets a `@Size(max=…)`** so an
oversized payload is rejected rather than processed or stored.

### Secrets

**Never commit a password, API key, token, or any secret to git — in any environment**,
not even throwaway local-dev credentials, so the habit is absolute and we never risk
leaking (or reusing) a real one. Secrets come only from environment variables
(`${JWT_SECRET}`, `${DB_INTERNAL_API_KEY}`, …) — no literal value and **no default** in
`application*.yaml`; a missing var should fail fast, not fall back to a baked-in value.
Non-secret connection details (host, port, service URLs) may be committed.

### Merging PRs

Branch → push → PR → checks pass → **squash merge** to `master`. GitHub squash uses the
**PR title** as the commit message, so make it a proper message (`feat: …`, `fix: …`), then
merge with an explicit subject:
```
gh pr merge <n> --squash --delete-branch \
  --subject "feat: describe the change (#<n>)" \
  --body "Optional longer description."
```
Never merge a PR titled "wip"/"draft".

### Commit messages

No attribution trailers (`attribution.commit` / `attribution.pr` are `""` in
`~/.claude/settings.json`, enforced at the tool level).

## Deployment

- Dockerized (multi-stage `Dockerfile`), deployed via **Coolify** (Hetzner) as a web
  service (`SPRING_PROFILES_ACTIVE=staging`) on both prod (`api.slapstat.com`) and staging
  (`api.staging.slapstat.com`). See `DEPLOYMENT.md`. Requires `NHL_SERVICE_URL` +
  `NHL_INTERNAL_API_KEY` (nhl-service) and `DATABASE_SERVICE_URL` + `DB_INTERNAL_API_KEY`
  (db-service) — the internal URLs use the services' Docker network aliases
  (`http://db-service:8086`, `http://nhl-service:8087`). Each `*_INTERNAL_API_KEY` is the
  value the matching downstream service exposes as its own `INTERNAL_API_KEY`.
- Health check: `/actuator/health`.
