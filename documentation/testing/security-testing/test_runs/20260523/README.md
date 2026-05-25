# MTL Explorer Security Test Report - 2026-05-23

Scope: local development server at `http://localhost:8080/mtl`, an updated verification server at `http://localhost:18082/mtl`, static review of security-sensitive client/server code, OpenAPI auth inventory, dependency/config scans, and targeted backend tests.

Privacy controls: no screenshots were taken, no API response bodies containing tracks/media were saved, and no track names, GPS coordinates, media filenames, or private route data are recorded here. Evidence is limited to status codes, headers, endpoint names, dependency metadata, and code references.

## Executive Summary

The initial test found 16 issues. I fixed 12 of them during this pass and verified the important auth, CORS, cache, dependency, planner, source-map, and logging changes against the updated local server. The upload-content sniffing heuristic was removed after review because it was not a reliable security control.

Remaining work:

- SEC-20260523-06: verify HTTPS reverse-proxy behavior, especially `Secure` cookies and HSTS, through the real TLS entrypoint.
- SEC-20260523-08: upload content validation remains open unless uploads are quarantined and accepted by the real conversion/parsing pipeline before entering the watched ingest folder.
- SEC-20260523-10: localStorage JWT remains by design; CSP and stale-token handling now reduce impact, but HttpOnly-cookie-only auth would be a larger design change.
- SEC-20260523-11: offline-cache stale-token behavior still needs controlled browser verification because that test can render private local data.

| ID | Severity | Status | Finding |
|---|---:|---|---|
| SEC-20260523-01 | High | (fixed) | Saved Bearer JWT remained valid after logout. |
| SEC-20260523-02 | High | (fixed) | Direct production dependency `axios@1.15.0` had active high-severity advisories. |
| SEC-20260523-03 | High | (fixed) | Raw client error logging could expose credentials or Authorization headers. |
| SEC-20260523-04 | Medium | (fixed) | CORS reflected origins that merely contained `localhost:`. |
| SEC-20260523-05 | Medium | (fixed) | Protected private responses had cacheable or missing `Cache-Control` headers. |
| SEC-20260523-06 | Medium | open | HTTPS reverse-proxy cookie/HSTS behavior still needs deployment verification. |
| SEC-20260523-07 | Medium | (fixed) | Login had no observed throttling for repeated bad attempts. |
| SEC-20260523-08 | Medium | partial | GPX upload accepted by extension before parser validation and could return raw exception text. |
| SEC-20260523-09 | Medium | (fixed) | Planner input/sidecar handling lacked coordinate/bbox hardening and timeout evidence. |
| SEC-20260523-10 | Medium | mitigated | Readable localStorage token keeps XSS blast radius high; CSP was missing. |
| SEC-20260523-11 | Medium | open | Offline cache behavior with stale local token still needs browser verification. |
| SEC-20260523-12 | Medium | (fixed) | Sensitive data redaction was incomplete across server logs, DB request logs, and browser logs. |
| SEC-20260523-13 | Low/Medium | (fixed) | Production source maps were generated and served. |
| SEC-20260523-14 | Low | (fixed) | OpenAPI contract had no Bearer auth security scheme. |
| SEC-20260523-15 | Low | (fixed) | Full logout readable-cookie cleanup only expired `path=/`. |
| SEC-20260523-16 | Low | (fixed) | GPS locate component logged exact current coordinates. |

## Verification Evidence

Updated-server auth checks:

| Check | Result |
|---|---:|
| Valid Bearer on `/api/info/build` | `200` |
| Valid cookie only on `/api/info/build` | `200` |
| Tampered Bearer plus valid cookie | `401` |
| Logout with cookie and Bearer | `200` |
| Cookie-only request after logout | `401` |
| Saved Bearer request after logout | `401` |

Updated CORS preflight target: `/api/tracks/get`, request headers `authorization, content-type`.

| Origin | HTTP | `Access-Control-Allow-Origin` | `Access-Control-Allow-Credentials` |
|---|---:|---|---|
| `http://localhost:5173` | `200` | echoed | `true` |
| `http://127.0.0.1:5173` | `200` | echoed | `true` |
| `http://notlocalhost:5173` | `403` | none | none |
| `https://evil.example` | `403` | none | none |

Updated headers/cache checks:

| Check | Result |
|---|---|
| `/api/info/build` cache | `Cache-Control: no-store` |
| Media bounds cache | `Cache-Control: no-store` |
| Track details cache | `Cache-Control: no-store` |
| CSP | present |
| Referrer Policy | `same-origin` |
| Permissions Policy | `camera=(), microphone=(), geolocation=(self)` |

Updated negative input checks:

| Check | Result |
|---|---:|
| Planner route with out-of-range coordinates | `400` |
| Planner route with unknown profile | `400` |
| Planner prewarm with missing bbox fields | `400` |
| Bad login attempts 1-5 | `401` |
| Bad login attempt 6 | `429` |

Dependency and build checks after fixes:

```text
npm audit --omit=dev --json
total: 0 vulnerabilities

npm audit --json
total: 0 vulnerabilities

npm run build
passed

mvn -pl mtl-client package
passed; rebuilt server static resources

mvn -pl mtl-server -Dtest=MapTileProxyControllerTest,WebUserSessionServiceTest test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0

mvn -pl mtl-api/mtl-api-typescript-fetch generate-resources
passed
```

Source-map verification after production rebuild:

```text
No .map files found in mtl-client/dist or mtl-server/src/main/resources/static.
No sourceMappingURL comments found in mtl-client/dist or mtl-server/src/main/resources/static.
```

OpenAPI auth metadata after regenerating from live `/mtl/v3/api-docs`:

```text
components.securitySchemes.bearerAuth: type=http, scheme=bearer, bearerFormat=JWT
global security: bearerAuth
servers: http://localhost:8080/mtl
```

Full backend suite note:

```text
mvn -pl mtl-server test
Tests run: 189, Failures: 0, Errors: 2
```

The two errors are the existing default-profile Spring context tests attempting `jdbc:postgresql://db:5432/mtl` from the local host. The updated server was verified with the `dev` profile against the local database, and targeted tests passed.

## Retest Evidence - 2026-05-23

Retest scope: current worktree, rebuilt production client bundle, server static resources recopied, and updated dev-profile server booted on `http://localhost:18082/mtl`. No screenshots were taken and no private API response bodies were saved.

Retest build and static checks:

```text
npm run build
passed

mvn -pl mtl-client package
passed

npm audit --omit=dev --json
total: 0 vulnerabilities

npm audit --json
total: 0 vulnerabilities

mvn -pl mtl-server -Dtest=MapTileProxyControllerTest,WebUserSessionServiceTest test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0

OpenAPI checked-in schema
bearerAuth present, global security present, servers preserved as http://localhost:8080/mtl

Source-map scan
no .map files and no sourceMappingURL comments in mtl-client/dist or mtl-server/src/main/resources/static
```

Retest live checks:

| Check | Result |
|---|---:|
| Live `/v3/api-docs` Bearer auth metadata | present |
| Valid Bearer on `/api/info/build` | `200` |
| Valid cookie only on `/api/info/build` | `200` |
| Tampered Bearer plus valid cookie | `401` |
| `/api/info/build` cache | `no-store` |
| `/api/info/build` CSP | present |
| `/api/info/build` Referrer Policy | `same-origin` |
| `/api/info/build` Permissions Policy | `camera=(), microphone=(), geolocation=(self)` |
| Media bounds cache | `no-store` |
| Track details cache | `no-store` |
| Production JS source-map URL | `404` |
| CORS `http://localhost:5173` | `200`, credentialed origin allowed |
| CORS `http://127.0.0.1:5173` | `200`, credentialed origin allowed |
| CORS `http://notlocalhost:5173` | `403`, no credentialed CORS headers |
| CORS `https://evil.example` | `403`, no credentialed CORS headers |
| Planner out-of-range coordinates | `400` |
| Planner unknown profile | `400` |
| Planner missing prewarm bbox | `400` |
| Logout with cookie and Bearer | `200` |
| Cookie-only request after logout | `401` |
| Saved Bearer request after logout | `401` |
| Bad login attempts 1-5 | `401` |
| Bad login attempt 6 | `429` |

Retest result: the 12 items marked `(fixed)` stayed fixed under repeat verification. SEC-20260523-06, SEC-20260523-08, SEC-20260523-10, and SEC-20260523-11 remain in the same non-fixed state described above because they require HTTPS deployment verification, real parser/quarantine upload validation, a larger auth design change, or controlled browser/offline-cache testing.

## Findings

### SEC-20260523-01 - Bearer JWT Survives Logout (fixed)

Initial evidence: after logout, cookie-only auth returned `401`, but the saved `Authorization: Bearer <old-token>` still returned `200` on `/api/info/build`.

Fix: `JwtAuthenticationFilter` now checks the JWT `user_session_id` against active, non-expired server-side session state through `WebUserSessionService.isSessionActive`.

Verification: saved Bearer after logout now returns `401`.

### SEC-20260523-02 - Vulnerable Axios In Auth Transport Path (fixed)

Initial evidence: `npm audit --omit=dev --json` reported a direct high finding set for `axios`; installed version was `1.15.0`.

Fix: upgraded Axios and related vulnerable frontend dependency graph entries.

Verification: production and full npm audits both report `0` vulnerabilities.

### SEC-20260523-03 - Raw Client Error Logging Can Expose Credentials Or Tokens (fixed)

Initial evidence: login/authenticated Axios paths logged raw error objects that can include request headers, request bodies, URLs, or credentials.

Fix: added `safeLogging.ts`, replaced high-risk raw error logging, and installed browser console redaction for `console.error`, `console.warn`, and `console.log`.

Verification: frontend type-check/build passed. Sanitizer redacts Bearer/JWT strings plus sensitive object keys including password, token, authorization, cookie, coordinates, filenames, track names, filter names, and search terms.

### SEC-20260523-04 - CORS Origin Check Is Substring-Based (fixed)

Initial evidence: `Origin: http://notlocalhost:5173` received credentialed CORS headers.

Fix: `MyCorsFilter` now parses origins and allows only local development hosts by exact host/scheme rules.

Verification: `http://notlocalhost:5173` and `https://evil.example` now return `403` with no credentialed CORS headers; `localhost` and `127.0.0.1` local dev origins are allowed.

### SEC-20260523-05 - Protected Private Responses Are Cacheable Or Missing No-Store (fixed)

Initial evidence: protected samples showed public or missing cache headers on dynamic private data.

Fix: dynamic/API responses now default to `Cache-Control: no-store`; user-data controller cache controls were changed away from public caching.

Verification: sampled build info, media bounds, and track detail responses all returned `Cache-Control: no-store`.

### SEC-20260523-06 - Reverse-Proxy HTTPS Cookie/HSTS Behavior Needs Verification

Initial evidence: local HTTP login correctly omitted `Secure`, but HTTPS reverse-proxy handling was not verified.

Mitigation added: `server.forward-headers-strategy: framework` is configured so app-level secure-request detection can honor forwarded headers when deployed behind a proxy.

Remaining verification: test through the real HTTPS entrypoint and confirm `Set-Cookie` includes `Secure` and HSTS is applied by the proxy or app boundary.

### SEC-20260523-07 - No Observed Login Throttling (fixed)

Initial evidence: repeated bad login attempts returned only `401`.

Fix: added in-memory per-remote-address throttling for bad login attempts. The limit uses the actual request remote address rather than caller-supplied forwarding headers. The failed-login map is periodically cleaned and capped so one-off source addresses cannot grow it without bound.

Verification: bad attempts 1-5 returned `401`; attempt 6 returned `429`.

### SEC-20260523-08 - GPX Upload Extension-Only Acceptance Path

Initial evidence: upload acceptance depended on supported filename format before copying bytes into the watched location.

Partial fix: `GpxUploadService` now rejects empty files, keeps the supported-extension allowlist, sanitizes filenames, and `GpxUploadController` returns controlled generic messages for service/internal failures.

Review outcome: the earlier byte-sniffing helper was removed. It caught obvious mistakes, but it was not a reliable security control because a malicious file can be made to look superficially valid while still attacking GPSBabel or the GPX parser.

Remaining fix: upload into a quarantine location outside the watched ingest tree, run the actual conversion/parsing pipeline with size/time limits, and move only accepted files into the watched folder. No valid private GPX was uploaded during this pass.

### SEC-20260523-09 - Planner Input And Sidecar Hardening Gaps (fixed)

Initial evidence: out-of-range route coordinates reached sidecar handling, missing prewarm bbox fields were accepted, and sidecar timeout/logging hardening was not evident.

Fix: planner route and prewarm inputs now validate waypoint count, coordinate ranges, profile names, bbox completeness, and bbox ordering before sidecar calls. `BRouterClient` uses configured request timeouts and sanitized failure logs.

Verification: out-of-range route, unknown profile, and missing bbox prewarm all returned `400`.

### SEC-20260523-10 - Readable LocalStorage JWT Raises XSS Impact

Initial evidence: JWT is stored in `localStorage` and no CSP was observed.

Mitigation added: CSP, Referrer Policy, and Permissions Policy are now emitted; client auth checks now reject expired tokens before considering the route authenticated.

Remaining risk: the readable localStorage JWT remains. Fully fixing this would require an auth design change, likely HttpOnly-cookie-only auth plus generated-client integration updates.

### SEC-20260523-11 - Offline Cache With Stale Token Needs Browser Verification

Initial evidence: static review found cached track data paths and route checks that previously relied on token presence.

Mitigation added: expired tokens are no longer accepted by `isAuthenticated()`.

Remaining verification: run the documented controlled browser test with cache populated, an expired/dummy token, and the backend blocked. This pass avoided that browser scenario to prevent rendering private local track data.

### SEC-20260523-12 - Sensitive Logging Redaction Is Incomplete (fixed)

Initial evidence: query strings persisted in DB request logs, full request headers could be logged at DEBUG, browser GPS coordinates were logged, raw Axios errors were logged, and sidecar URLs/errors could include coordinate parameters.

Fix: server request logs redact query strings, request-header logs redact security headers, browser logging goes through centralized sanitization, exact GPS coordinate logs were removed, BRouter logs were sanitized, upload/server exception messages were generalized, and planned-track save logs no longer include names.

Verification: static review of changed logging paths plus successful frontend/server builds.

### SEC-20260523-13 - Production Source Maps Are Served (fixed)

Initial evidence: `vite.config.ts` generated source maps and the Spring static bundle served `.js.map` assets.

Fix: production source maps are disabled, and the client Maven package cleaned and recopied server static assets.

Verification: no `.map` files and no `sourceMappingURL` comments were found in `mtl-client/dist` or `mtl-server/src/main/resources/static`.

### SEC-20260523-14 - OpenAPI Auth Contract Missing (fixed)

Initial evidence: `components.securitySchemes` and global `security` were absent from `mtl-api/open-api-schema/schema.json`.

Fix: added OpenAPI Bearer auth configuration and public-operation exceptions, downloaded the live schema from `/mtl/v3/api-docs`, preserved the normal local server URL, and regenerated the TypeScript fetch client.

Verification: schema now contains global `bearerAuth`; generator completed successfully.

### SEC-20260523-15 - Full Logout Cookie Cleanup Misses `/mtl/` (fixed)

Initial evidence: `clearReadableCookies()` expired readable cookies only with `path=/`.

Fix: full logout readable-cookie cleanup now expires cookies at both `/` and `/mtl/`.

Verification: frontend build/type-check passed.

### SEC-20260523-16 - GPS Locate Logs Exact Coordinates (fixed)

Initial evidence: `GpsLocate.vue` logged current longitude/latitude when device location succeeded.

Fix: exact coordinate logging was removed.

Verification: static search confirms the identified GPS locate coordinate log is gone; frontend build/type-check passed.

## Clean Checks

- Missing, malformed, and tampered JWTs were rejected.
- Bearer token priority over a valid cookie was enforced.
- Cookie-only auth worked for protected cookie-backed requests before logout.
- Map proxy rejected invalid scope, encoded traversal, and full PMTiles download without `Range`.
- Protected endpoint inventory did not reveal unexpected unauthenticated non-public OpenAPI endpoints.
- PWA runtime caching is limited to background assets; API navigation fallback is denied in Vite PWA config.
- No screenshots or private response bodies were collected.

## Limitations

- Browser offline-cache verification was not executed because it can render private local track data.
- HTTPS reverse-proxy behavior was not tested because this pass used local HTTP and a temporary local server port.
- No valid `.gpx` content was uploaded to avoid writing into the private local ingest directory.
- Maven vulnerability scanning with OWASP Dependency-Check was not run.
- The full backend test suite still has a local default-profile database prerequisite issue unrelated to these fixes; targeted tests passed.
