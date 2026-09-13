# Deployment — fantasy-bff

The BFF is deployed via **Coolify** (self-hosted on Hetzner) as a **Docker web service**
built from the multi-stage [`Dockerfile`](./Dockerfile). It talks to four downstream services
over the internal Docker network: `fantasy-db-service` (users, projections, shares),
`fantasy-yahoo-service` (Yahoo OAuth, leagues and the Yahoo player pool),
`fantasy-espn-service` (ESPN leagues and the ESPN player pool) and
`fantasy-projection-service` (the projection model and the Who's hot splits).

| Environment | Public URL | Web origin (CORS) |
|---|---|---|
| production | `https://api.slapstat.com` | `https://slapstat.com` |
| staging | `https://api.staging.slapstat.com` | `https://staging.slapstat.com` |

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — multi-stage, JDK 25 builds the boot jar, JRE 25 runs it |
| Profiles | **None required.** Everything a deployed instance needs is in `application.yaml`, driven by env vars. Staging sets `SPRING_PROFILES_ACTIVE=staging` only to permit the API-doc URLs for QA; production sets no profile. Never deploy with `dev`: it logs password-reset links. |
| Port | App binds to `${PORT}` (defaults to 8080); Coolify routes the domain → 8080 |
| Health check | `GET /actuator/health`, curled inside the container. It includes readiness, which goes to `REFUSING_TRAFFIC` (503) when a downstream answers the startup key check with 401 — so a deploy with a mismatched key fails its health check and rolls back. |
| TLS | Coolify provisions Let's Encrypt for the domain (Cloudflare DNS must be **DNS-only / grey cloud**) |

## Environment variables (set in Coolify, per environment)

Built from [`application.yaml`](./src/main/resources/application.yaml), which is the source of
truth: when this table and that file disagree, the file wins and this table is stale.

**Required** means the app does not start when the variable is missing or blank. Every
secret is environment-specific: staging and production never share one.

### Downstream services

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `DATABASE_SERVICE_URL` | | deployed | `http://localhost:8086` | Deployed: `http://db-service:8086` (the stable network alias). |
| `DB_INTERNAL_API_KEY` | yes | **yes** | none | **Same value** as `INTERNAL_API_KEY` on db-service. |
| `YAHOO_SERVICE_URL` | | deployed | `http://localhost:8088` | Deployed: `http://yahoo-service:8088`. |
| `YAHOO_INTERNAL_API_KEY` | yes | **yes** | none | **Same value** as `INTERNAL_API_KEY` on yahoo-service. |
| `ESPN_SERVICE_URL` | | deployed | `http://localhost:8090` | Deployed: `http://espn-service:8090`. |
| `ESPN_INTERNAL_API_KEY` | yes | **yes** | none | **Same value** as `INTERNAL_API_KEY` on espn-service. |
| `PROJECTION_SERVICE_URL` | | deployed | `http://localhost:8092` | Deployed: `http://projection-service:8092`. |
| `PROJECTION_INTERNAL_API_KEY` | yes | **yes** | none | **Same value** as `INTERNAL_API_KEY` on projection-service. |
| `ESPN_IMAGE_BASE_URL` | | no | `https://a.espncdn.com` | ESPN's public headshot CDN. |

### Sign-in and access

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `JWT_SECRET` | yes | **yes** | none | ≥32 chars (HS256). Generate with `openssl rand -hex 32`. |
| `WEB_ORIGIN` | | deployed | `http://localhost:4200` for links, empty for CORS | The deployed web origin. The base for email links and the Google redirect URI, and the CORS allowlist unless `CORS_ALLOWED_ORIGINS` is set. |
| `CORS_ALLOWED_ORIGINS` | | no | `WEB_ORIGIN` | Comma-separated. Staging sets `https://staging.slapstat.com,http://localhost:4200` so `npm run start:staging` can reach it; **production leaves it unset**. |
| `ADMIN_EMAILS` | | no | empty (no admins) | Comma-separated; these accounts get the `admin` JWT claim. |
| `GOOGLE_CLIENT_ID` | | no | empty (Google sign-in off) | Public OAuth Client ID. |
| `GOOGLE_CLIENT_SECRET` | yes | no | empty | Needed by the code flow (`/api/v1/auth/google/code`), which rejects every request without it. |
| `GOOGLE_REDIRECT_URIS` | | no | `<WEB_ORIGIN>/auth/google/callback` | Comma-separated allowlist; each must also be registered on the Google client. |
| `GOOGLE_TOKEN_URI` | | no | Google's token endpoint | For tests. |
| `FACEBOOK_APP_ID` | | no | empty (Facebook sign-in off) | Public; the same value the web ships. |
| `FACEBOOK_APP_SECRET` | yes | no | empty | `/api/v1/auth/facebook` rejects every request unless both Facebook variables are set. |
| `FACEBOOK_GRAPH_BASE_URL` | | no | `https://graph.facebook.com` | For tests. |
| `RATE_LIMIT_ENABLED` | | no | `true` | Per-IP limits on the public endpoints. |
| `SWAGGER_ENABLED` | | no | `false` | `true` exposes Swagger UI and `/v3/api-docs`: staging only, never production. |
| `SPRING_PROFILES_ACTIVE` | | no | none | Staging: `staging`. **Leave unset in production.** |

### Email

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `RESEND_API_KEY` | yes | **yes** | none | Sends the password-reset and verification emails. Only the `dev` profile may leave it blank; the links are then logged instead. |
| `EMAIL_FROM` | | no | `SlapStat <no-reply@slapstat.com>` | Sender identity. |
| `RESEND_BASE_URL` | | no | `https://api.resend.com` | For tests. |

### Features and the player pool

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `PLAYERS_SOURCE` | | no | `yahoo` | `yahoo` or `espn`. Player ids differ between the two, so this flips only together with migrating stored projections. |
| `PROJECTION_MODEL_ENABLED` | | no | `false` | Opens `/api/v1/projection-model/**`: the model's output **and** the Who's hot splits. Off also takes the AI projection away. |
| `AI_PROJECTION_ENABLED` | | no | `true` | The AI projection (model-seeded start). Served only where `PROJECTION_MODEL_ENABLED` is also on. |

### Payments

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `PAYMENTS_ENABLED` | | no | `false` | Subscription billing. |
| `PAYMENTS_PROVIDER` | | no | `mock` | `mock` or `paddle`. |
| `PAYMENTS_MOCK_WEBHOOK_SECRET` | yes | with `mock` | empty | Signs the mock provider's tokens and webhooks; the mock flow fails without it. |
| `PAYMENTS_MOCK_SELF_BASE_URL` | | no | `http://localhost:8080` | Where the mock posts its own webhook. |
| `PADDLE_API_BASE_URL` | | with `paddle` | `https://sandbox-api.paddle.com` | **The default is the sandbox.** Production must set `https://api.paddle.com`, together with a live key. |
| `PADDLE_API_KEY` | yes | with `paddle` | empty | Server-side API key; sandbox and live keys belong to separate accounts. |
| `PADDLE_WEBHOOK_SECRET` | yes | with `paddle` | empty | Verifies the `Paddle-Signature` header. |
| `PADDLE_PRICE_ID` | | with `paddle` | empty | The recurring price a checkout subscribes to (`pri_…`). |
| `PADDLE_CHECKOUT_URL` | | no | empty | Our page that hosts the checkout; blank falls back to Paddle's default payment link. |
| `PADDLE_TIMEOUT_MS` | | no | `10000` | |
| `PADDLE_SIGNATURE_TOLERANCE_SECONDS` | | no | `300` | How old a webhook timestamp may be before it counts as a replay. |

"With `mock`" / "with `paddle`" means required when `PAYMENTS_ENABLED=true` and that provider
is selected. The app still starts without them; the payment flow is what fails.

### Seasons and the model

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `YAHOO_STATS_SEASON` | | no | `2025` | Start year of the stat line the player reads show. |
| `ESPN_STATS_SEASON` | | no | `2025` | The same, for the ESPN pool. Must equal `YAHOO_STATS_SEASON`. |
| `PROJECTION_SEASON` | | no | `2026` | Start year of the season the model projects. |
| `PROJECTION_MODEL_VERSION` | | no | empty (latest run) | Naming a version pins it; that is the rollback path. |
| `PROJECTION_PLAYER_ID_OVERRIDES` | | no | empty | `nhlId:platformId` pairs for players name matching cannot reach. |

### Timeouts, caches and operations

| Variable | Secret | Required | Default | Notes |
|---|---|---|---|---|
| `DB_MIGRATION_TIMEOUT_MS` | | no | `180000` | The one-off player-id remap. |
| `DB_PROJECTION_TIMEOUT_MS` | | no | `15000` | Loading or saving a whole projection. |
| `ESPN_PLAYER_STATS_TTL_MS` | | no | `1800000` | In-memory cache of ESPN stat lines. |
| `PROJECTION_PLAYER_MAPPING_TTL_MS` | | no | `1800000` | In-memory cache of the NHL id → platform id mapping. |
| `PORT` | | no | `8080` | |
| `APP_VERSION` | | no | `dev` | Served on `/actuator/info`; the deploy sets it. |
| `SENTRY_DSN` | | no | unset (Sentry off) | Read by the Sentry SDK, not `application.yaml`. |
| `SENTRY_ENVIRONMENT` | | no | unset | Tells staging and production apart; both report into the same Sentry project. |
| `SENTRY_RELEASE` | | no | unset | Set on production, not on staging. |

## First-time setup

**Deploy db-service, yahoo-service, espn-service and projection-service first** (see their
`DEPLOYMENT.md`), then in Coolify:

1. Create an application from this repo (GitHub App source, **Dockerfile** build pack) in
   the target environment, on the target server.
2. Set the variables above: every **required** one, plus `WEB_ORIGIN` and the four service
   URLs. Match each `*_INTERNAL_API_KEY` to that downstream's own `INTERNAL_API_KEY`.
3. Set the **Domains** (e.g. `https://api.staging.slapstat.com`) and deploy.
4. Add a Cloudflare **A record (DNS only / grey cloud)** for the domain → the server IP so
   TLS can provision.
5. Verify: `GET /actuator/health` is `UP`, the log shows "Internal API key accepted" for all
   four services, and you can register a user, log in and receive a JWT.

## Local development

```bash
# Start the downstream services first (see their DEPLOYMENT.md / CLAUDE.md)
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 48) \
  DB_INTERNAL_API_KEY=… YAHOO_INTERNAL_API_KEY=… ESPN_INTERNAL_API_KEY=… PROJECTION_INTERNAL_API_KEY=… \
  ./gradlew bootRun
```

Every internal key is needed locally too: the downstreams refuse calls without one, and the
BFF will not start without them. Use the same value you gave each service as its
`INTERNAL_API_KEY`.

The `dev` profile enables CORS from `http://localhost:4200` and Swagger UI, and is the only
profile that runs without `RESEND_API_KEY`: reset and verification links are then written to
the log instead of being emailed.
