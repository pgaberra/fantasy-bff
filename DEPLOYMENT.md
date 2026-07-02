# Deployment — fantasy-bff

The BFF is deployed via **Coolify** (self-hosted on Hetzner) as a **Docker web service**
built from the multi-stage [`Dockerfile`](./Dockerfile). It talks to the real downstream
services over the internal Docker network: `fantasy-db-service` (users) and
`fantasy-nhl-service` (player stats).

| Environment | Public URL | Web origin (CORS) |
|---|---|---|
| production | `https://api.slapstat.com` | `https://slapstat.com` |
| staging | `https://api.staging.slapstat.com` | `https://staging.slapstat.com` |

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — multi-stage, JDK 25 builds the boot jar, JRE 25 runs it |
| Profiles | `SPRING_PROFILES_ACTIVE=staging` (the deployed profile, used by **both** prod and staging) |
| Port | App binds to `${PORT}` (defaults to 8080); Coolify routes the domain → 8080 |
| Health check | `GET /actuator/health` |
| TLS | Coolify provisions Let's Encrypt for the domain (Cloudflare DNS must be **DNS-only / grey cloud**) |

## Environment variables (set in Coolify, per environment)

| Key | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` |
| `JWT_SECRET` | Random, ≥256-bit (≥32 chars for HS256). Generate with `openssl rand -hex 32`. |
| `DATABASE_SERVICE_URL` | Internal URL of db-service — `http://db-service:8086` (its stable network alias). |
| `DB_INTERNAL_API_KEY` | Shared secret for BFF → db-service auth. **Same value** as `INTERNAL_API_KEY` on `fantasy-db-service`. |
| `NHL_SERVICE_URL` | Internal URL of nhl-service — `http://nhl-service:8087`. |
| `NHL_INTERNAL_API_KEY` | Shared secret for BFF → nhl-service auth. **Same value** as `INTERNAL_API_KEY` on `fantasy-nhl-service`. |
| `YAHOO_SERVICE_URL` | Internal URL of yahoo-service — `http://yahoo-service:8088`. |
| `YAHOO_INTERNAL_API_KEY` | Shared secret for BFF → yahoo-service auth. **Same value** as `INTERNAL_API_KEY` on `fantasy-yahoo-service`. |
| `WEB_ORIGIN` | The deployed web origin, used for CORS (e.g. `https://staging.slapstat.com`). |
| `GOOGLE_CLIENT_ID` | Public Google OAuth Client ID (not a secret); when unset, `/api/v1/auth/google` rejects all requests. |
| `FACEBOOK_APP_ID` | Public Facebook App ID (not a secret) — the same value the web app ships. |
| `FACEBOOK_APP_SECRET` | Facebook App Secret (a **real secret**). When either Facebook var is unset, `/api/v1/auth/facebook` rejects all requests. |

> Each `*_INTERNAL_API_KEY` and `JWT_SECRET` is environment-specific — staging and prod
> use **independent** secrets, never shared.

## First-time setup

**Deploy `fantasy-db-service` and `fantasy-nhl-service` first** (see their `DEPLOYMENT.md`),
then in Coolify:

1. Create an application from this repo (GitHub App source, **Dockerfile** build pack) in
   the target environment, on the target server.
2. Set the env vars above. Use the downstream services' stable network aliases
   (`db-service` / `nhl-service`) for the internal URLs, and match each `*_INTERNAL_API_KEY`
   to the downstream's `INTERNAL_API_KEY`.
3. Set the **Domains** (e.g. `https://api.staging.slapstat.com`) and deploy.
4. Add a Cloudflare **A record (DNS only / grey cloud)** for the domain → the server IP so
   TLS can provision.
5. Verify: `GET /actuator/health` is `UP`, then register a user, log in, receive a JWT.

## Local development

```bash
# Start Postgres + db-service + nhl-service first (see their DEPLOYMENT.md / CLAUDE.md)
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```

The `dev` profile enables CORS from `http://localhost:4200` and Swagger UI.
No `DB_INTERNAL_API_KEY` / `NHL_INTERNAL_API_KEY` needed locally — the downstream
filters are disabled when the key is unset.
