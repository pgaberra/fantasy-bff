# Deployment — fantasy-bff (Render staging)

The BFF is deployed to Render as a **Docker web service** defined by
[`render.yaml`](./render.yaml). In staging it runs with the `mock` profile, so
all downstream calls (NHL, database, Yahoo) are stubbed and no external services
are required.

## How it runs

| Aspect | Value |
|---|---|
| Build | `Dockerfile` — multi-stage, JDK 25 builds the boot jar, JRE 25 runs it |
| Profiles | `SPRING_PROFILES_ACTIVE=mock,staging` |
| Port | App binds to `${PORT}` (Render injects it); defaults to 8080 locally |
| Health check | `GET /actuator/health` |
| Auto-deploy | On every push to `master` |

## Environment variables

| Key | Set by | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `render.yaml` | `mock,staging` |
| `JWT_SECRET` | `render.yaml` (`generateValue`) | Random, ≥256-bit. Must be ≥32 chars for HS256. |
| `WEB_ORIGIN` | **You, in the dashboard** | The deployed web URL, used for CORS. Set after the web site exists, then redeploy. |

## First-time setup

1. Render → **New → Blueprint** → connect this repo. It reads `render.yaml` and
   creates the `fantasy-bff-staging` service. Deploy it.
2. Copy the service URL, e.g. `https://fantasy-bff-staging.onrender.com`.
3. Deploy the web app (see fantasy-web `DEPLOYMENT.md`) using that URL.
4. Back here: **Environment → add `WEB_ORIGIN`** = the web URL → save. This
   redeploys and allows CORS requests from the front end.

## Mock login (staging)

```
email:    mock@example.com
password: mockpassword123
```
Override via `MOCK_USER_EMAIL` / `MOCK_USER_PASSWORD` env vars if desired.

## Local equivalent

```bash
SPRING_PROFILES_ACTIVE=mock JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```
