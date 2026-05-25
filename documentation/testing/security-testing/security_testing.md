# MTL Explorer Security Testing

Security test plan for the MTL Explorer client and server. Run these checks only
against local development or disposable test environments.

## Current Auth Model

| Area | Codebase fact | Test focus |
|---|---|---|
| Protected API | `WebSecurityConfig` requires authentication for `/api/**`. | Every non-public API endpoint rejects missing or bad JWT credentials. |
| Public API | `OPTIONS /**`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/demo-status`, and `/api/map/status` are public. | Only intended public endpoints are reachable without credentials. |
| JWT sources | `JwtAuthenticationFilter` reads `Authorization: Bearer ...` first, then the `mtl_jwt` cookie. | Bearer priority is enforced, including invalid Bearer plus valid cookie. |
| Login output | `AuthController` returns the JWT in the response body and sets the `mtl_jwt` cookie. | Cookie flags and token content are correct after login. |
| Cookie flags | `mtl_jwt` is `HttpOnly`, `SameSite=Strict`, path `/mtl/`, and `Secure` when the request is HTTPS. | Local HTTP does not set `Secure`; HTTPS/reverse proxy deployments do. |
| Client storage | `auth.ts` stores the readable token in `localStorage` key `mtl.jwt`. | Client sends fresh Bearer headers through `apiClient` and generated OpenAPI clients. |
| Session state | Login creates a `user_session_id`; logout records the session as logged out. | Old Bearer tokens are rejected after logout. |
| Reverse proxy | Docker app traffic is normally HTTP behind a TLS proxy. | Forwarded-proto handling still causes HTTPS logins to set `Secure` cookies and HSTS at the proxy/app boundary. |

OpenAPI follow-up: `mtl-api/open-api-schema/schema.json` should declare JWT
Bearer auth globally and document the intentionally public exceptions.

## Local Setup

Use credentials from `mtl-server/src/main/resources/application-dev.yml`.

```bash
export BASE='http://localhost:8080/mtl'
export USERNAME='temp'
export PASSWORD='temp.42'
export COOKIE_JAR='/tmp/mtl-security-cookies.txt'
```

For Docker Compose smoke tests, set `BASE='http://localhost:18080/mtl'` and use
the configured Compose credentials instead.

## JWT Required Matrix

| Case | Request | Expected result |
|---|---|---|
| No token | Protected endpoint without `Authorization` and without cookie. | `401` with JSON unauthorized body. |
| Malformed token | `Authorization: Bearer not-a-jwt`. | `401`; request is not authenticated. |
| Tampered token | Change any character in a real token signature. | `401`; signature validation fails. |
| Expired token | Use a token whose `exp` is in the past. | `401`; client clears local token and redirects to `/login`. |
| Valid Bearer | `Authorization: Bearer $TOKEN`. | Protected endpoint succeeds. |
| Valid cookie only | Send only `mtl_jwt` cookie. | Protected fetch/media-style endpoint succeeds. |
| Invalid Bearer plus valid cookie | Send valid cookie and invalid Bearer. | `401`; Bearer header wins over cookie. |
| Logout | POST `/api/auth/logout` with cookie. | Server marks session logged out when possible and clears `mtl_jwt`. |
| Old Bearer after logout | Save a valid Bearer token, log out, then retry the saved Bearer token. | `401` if logout is meant to revoke server-side sessions; otherwise document the bearer-token lifetime risk. |

## Curl Smoke Checks

Login and capture both the readable JWT and HttpOnly cookie:

```bash
LOGIN_JSON=$(
  curl -sS -i -c "$COOKIE_JAR" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
    "$BASE/api/auth/login"
)

printf '%s\n' "$LOGIN_JSON"
TOKEN=$(printf '%s\n' "$LOGIN_JSON" | sed -n '/^{/,$p' | jq -r '.token')
```

Verify unauthenticated access is blocked:

```bash
curl -i "$BASE/api/info/build"
curl -i "$BASE/api/tracks/get"
```

Verify valid Bearer access:

```bash
curl -i -H "Authorization: Bearer $TOKEN" "$BASE/api/info/build"
curl -i -H "Authorization: Bearer $TOKEN" "$BASE/api/tracks/get"
```

Verify valid cookie-only access:

```bash
curl -i -b "$COOKIE_JAR" "$BASE/api/info/build"
curl -i -b "$COOKIE_JAR" "$BASE/api/media/get-media-in-bounds?minLat=0&minLng=0&maxLat=1&maxLng=1"
```

Verify bad-token handling:

```bash
curl -i -H 'Authorization: Bearer not-a-jwt' "$BASE/api/info/build"
curl -i -b "$COOKIE_JAR" -H 'Authorization: Bearer not-a-jwt' "$BASE/api/info/build"

TAMPERED_TOKEN="${TOKEN%?}x"
curl -i -H "Authorization: Bearer $TAMPERED_TOKEN" "$BASE/api/info/build"
```

Verify logout clears the cookie:

```bash
curl -i -b "$COOKIE_JAR" -c "$COOKIE_JAR.after" -X POST "$BASE/api/auth/logout"
cat "$COOKIE_JAR.after"
curl -i -b "$COOKIE_JAR.after" "$BASE/api/info/build"
curl -i -H "Authorization: Bearer $TOKEN" "$BASE/api/info/build"
```

Expected response indicators:

| Check | Expected evidence |
|---|---|
| Login | HTTP `200`, response body includes `token`, and `Set-Cookie: mtl_jwt=...; HttpOnly; SameSite=Strict; Path=/mtl/`. |
| Missing/bad token | HTTP `401` and `{"error":"Unauthorized"}`. |
| Valid token | HTTP `200` on protected endpoints. |
| Logout | `Set-Cookie` expires `mtl_jwt`; subsequent cookie-only request returns `401`. |
| Old Bearer after logout | Subsequent saved-Bearer request returns `401`, or the risk is recorded as accepted. |

## Endpoint Inventory

Use the OpenAPI schema as the starting point:

```bash
jq -r '.paths | keys[]' mtl-api/open-api-schema/schema.json
```

Classify each path as public or protected:

| Classification | Expected paths |
|---|---|
| Public | `/api/auth/login`, `/api/auth/logout`, `/api/auth/demo-status`, `/api/map/status`, `OPTIONS /**`. |
| Protected | All other `/api/**` paths, including admin, analytics, config, data freshness, filter, Garmin export, GPX upload, indexer, jobs, location search, map config/proxy, media, planner, tracks, and chart series. |

For each protected path, run a no-token check and one valid-token check. For
state-changing endpoints, use a disposable database and harmless fixture data.

## Client Tests

Manual browser checks:

| Area | Steps | Expected result |
|---|---|---|
| Route guard | Open `/mtl/`, `/mtl/track/1`, `/mtl/plan`, and `/mtl/stats` without token. | Browser lands on `/mtl/login`. |
| Login | Log in with dev credentials. | Token appears under the `localStorage` key `mtl.jwt`; app navigates to map. |
| Authenticated login route | Open `/mtl/login` while authenticated. | Router returns to home. |
| 401 redirect | Replace the `localStorage` key `mtl.jwt` with `not-a-jwt` and reload. | First protected API failure clears token and redirects to login. |
| Logout credentials only | Use the admin credentials logout action. | Local token is removed; server cookie is cleared; preferences/cache stay. |
| Full logout | Use the full logout action. | Local/session storage, IndexedDB track cache, Cache Storage, readable cookies, and service workers are cleared where browser APIs allow it. |
| Token leakage | Inspect network requests, address bar, logs, and media URLs. | JWT never appears in URLs; Bearer headers and HttpOnly cookie are the only transport mechanisms. |
| Service worker/cache | Login, load app, logout, then inspect Cache Storage and offline behavior. | Protected API responses are not served to a logged-out browser as fresh authenticated data. |
| Console redaction | Force failed login and failed protected requests, then inspect console objects. | Passwords, Authorization headers, cookies, and raw Axios configs are not logged. |
| Offline stale token | Populate track cache, replace `mtl.jwt` with a dummy or expired token, block the backend, and reload. | App redirects to login or otherwise refuses to render cached private data until auth is revalidated. |
| Readable cookie cleanup | Set a synthetic readable cookie at `/mtl/`, run full logout, inspect `document.cookie`. | App-scoped readable cookies are removed along with `/` cookies. |
| GPS logging | Use device-location controls with console open. | Exact current coordinates are not printed to console logs. |

Suggested Vitest coverage:

- `auth.ts`: `mtl.jwt` storage, JWT payload parsing, expiry parsing, logout cleanup, and 401 redirect behavior.
- `apiClient.ts`: request interceptor adds a fresh Bearer header per request and response interceptor handles `401`/`403`.
- `openApiClient.ts`: generated-client `Configuration` includes current Bearer header, `credentials: 'include'`, and auth-failure middleware.
- `router/index.ts`: unauthenticated protected routes redirect; authenticated `/login` redirects home.

## Server Tests

Suggested Spring integration coverage:

| Component | Test cases |
|---|---|
| `WebSecurityConfig` | Public allowlist works; every other `/api/**` request requires auth; non-API SPA/static routes remain public. |
| `AuthController` | Good login returns body token and cookie; bad login returns `401`; logout clears cookie and marks session when a JWT session id exists. |
| `JwtAuthenticationFilter` | Bearer token authenticates; cookie token authenticates; Bearer priority over cookie; missing `user_session_id`, unknown user, expired token, malformed token, and tampered token do not authenticate. |
| `JwtUtil` | Generated tokens include subject, `iat`, `exp`, and `user_session_id`; validation rejects expired or modified tokens. |

Run these as automated tests once added:

```bash
cd mtl-server
mvn test
```

## CORS And CSRF

| Check | Steps | Expected result |
|---|---|---|
| Local dev CORS | Send requests with `Origin: http://localhost:5173`. | Response echoes the localhost origin and includes `Access-Control-Allow-Credentials: true`. |
| Non-local origin | Send requests with a non-localhost `Origin`. | Response does not grant credentialed cross-origin access. |
| Preflight | Send `OPTIONS` with authorization/content-type request headers. | Preflight succeeds only with expected methods and headers. |
| Cookie CSRF | From a different origin, attempt a credentialed POST to a protected endpoint. | `SameSite=Strict` cookie is not sent by the browser; missing Bearer request is rejected. |
| Login CSRF impact | Attempt cross-origin POST to `/api/auth/login`. | Login may be public, but credentialed reads/writes still require valid JWT transport and browser cookie rules. |
| Origin allowlist strictness | Test `localhost`, `127.0.0.1`, and origins that merely contain `localhost`. | Only exact intended local origins receive `Access-Control-Allow-Origin` with credentials. |

Example:

```bash
curl -i -X OPTIONS "$BASE/api/tracks/get" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization, content-type'
```

## Upload, Media, And Proxy Checks

| Area | Checks |
|---|---|
| GPX upload | Missing token is rejected; invalid content type is rejected; oversized files respect configured multipart limits; parser errors do not leak filesystem paths or stack traces. |
| GPX content validation | Upload harmless non-GPX bytes with a `.gpx` filename in a disposable environment. | Upload is rejected before indexing, or downstream parser failure is controlled and path-free. |
| Media metadata | Missing token is rejected; ids that do not exist return controlled errors; metadata does not expose unnecessary local paths beyond intended admin/debug surfaces. |
| Media content | Missing token is rejected; range requests, content type, ETag, and private cache headers behave consistently; image resize bounds are enforced. |
| Map proxy | Missing token is rejected; invalid scope, path traversal (`../`, `%2e%2e`), path separators, suspicious filenames, and full PMTiles downloads without allowed range are rejected. |
| Planner and sidecars | Missing token is rejected; bad profiles/coordinates/bounding boxes return validation errors; BRouter failures do not leak internal service details or hang past configured timeouts. |

## Headers, Logs, Dependencies

| Area | Checks |
|---|---|
| Security headers | Verify `X-Content-Type-Options`, frame protection, HSTS on HTTPS deployments, Referrer Policy, Permissions Policy, and whether a CSP is needed. |
| Cache headers | Protected dynamic responses should be `no-store` unless a controller intentionally sets private cache headers. Static assets may be public/immutable. Media, tracks, chart series, and location-derived responses should not be `public`. |
| Logs | Debug request-header logging must not be enabled in production. Verify Authorization headers, JWTs, passwords, cookies, coordinates, search terms, private filenames, and raw client error objects are not persisted in app logs, browser logs, or `web_request_log`. |
| Defaults and secrets | Scan committed files and Docker defaults for production-unsafe credentials. Document that `change-me`, `temp.42`, and database defaults must be changed before exposure. |
| Dependency audit | Run production dependency audit separately from full dev-toolchain audit. Treat findings by exploitability and deployed surface. |
| Source maps | Verify whether production source maps are generated and served. | Public deployments do not expose source maps unless that is an explicit supportability choice. |
| Login abuse | Send repeated bad login attempts from one source. | Attempts are rate-limited, delayed, locked out, or at least security-logged. |

Suggested commands:

```bash
rg -n "password|secret|token|Authorization|mtl_jwt|change-me|temp\\.42|MtlSuper123" \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**'

cd mtl-client
npm audit

cd ../mtl-server
mvn dependency:tree
```

## OWASP Checklist Anchors

Use these references to make sure the local checklist does not miss common
classes of issues:

- [OWASP Web Security Testing Guide Latest](https://owasp.org/www-project-web-security-testing-guide/latest/)
- [OWASP API Security Top 10 2023](https://owasp.org/API-Security/editions/2023/en/0x00-header/)
- [OWASP Top 10 2025](https://owasp.org/Top10/2025/0x00_2025-Introduction/)
