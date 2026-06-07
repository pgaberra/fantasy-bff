# Deployment — fantasy-bff (Render staging)

The BFF is deployed to Render as a **Docker web service** defined by
[`render.yaml`](./render.yaml). In staging it runs with the `mock` profile, so
all downstream calls (NHL, database, Yahoo) are stubbed and no external services
are required.

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — multi-stage, JDK 25 builds the boot jar, JRE 25 runs it |
| Profiles | `SPRING_PROFILES_ACTIVE=staging` |
| Port | App binds to `${PORT}` (Render injects it); defaults to 8080 locally |
| Health check | `GET /actuator/health` |
| Auto-deploy | On every push to `master` |

## Environment variables

| Key | Set by | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `render.yaml` | `staging` |
| `JWT_SECRET` | `render.yaml` (`generateValue`) | Random, ≥256-bit. Must be ≥32 chars for HS256. |
| `DATABASE_SERVICE_URL` | `render.yaml` | URL of the deployed `fantasy-db-service`. |
| `INTERNAL_API_KEY` | **You, in the dashboard** | Shared secret for BFF → db-service auth. Same value as on `fantasy-db-service`. Generate with `openssl rand -hex 32`. |
| `WEB_ORIGIN` | **You, in the dashboard** | The deployed web URL, used for CORS. Set after the web site exists, then redeploy. |

## First-time setup

**Deploy `fantasy-db-service` first** (see its `DEPLOYMENT.md`), then:

1. Render → **New → Blueprint** → connect this repo. It reads `render.yaml` and
   creates the `fantasy-bff-staging` service. Deploy it.
2. Set `INTERNAL_API_KEY` to the same value used on `fantasy-db-service`.
3. Set `WEB_ORIGIN` to the deployed web URL after the static site is deployed.
4. Redeploy. Verify: register a user, log in, receive a JWT.

## Local development

To run locally with the real db-service (no mock):

```bash
# Start Postgres and db-service first (see fantasy-db-service DEPLOYMENT.md)
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```

The `dev` profile enables CORS from `http://localhost:4200` and Swagger UI.
No `INTERNAL_API_KEY` needed locally — the filter is disabled when the key is unset.

To still use the in-memory mock locally (no db-service required):

```bash
SPRING_PROFILES_ACTIVE=mock,dev JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```
