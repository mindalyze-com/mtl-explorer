# Google Health OAuth

## Decision

MTL Explorer supports a user-owned Google Cloud project in **Testing** status.
The user adds the Google account that holds the health data as a test user and
creates a **Desktop app** OAuth client.

Testing-mode refresh tokens expire after seven days. MTL Explorer must treat
regular reauthorization as part of the normal product flow, not as an unusual
error. Testing is the permanent setup, not a temporary step: users do not publish the
app, move it to **In production**, or have it verified by Google.

## Supported authorization flow

- Request offline access with PKCE `S256`, a random single-use state, and
  `prompt=consent`.
- Redirect to the dead loopback address
  `http://127.0.0.1:1/mtl-explorer-oauth`.
- Tell the user that the browser error is expected.
- Accept the complete address copied from the browser. Validate its host,
  port, authorization result, and state before exchanging the code.
- Use the same redirect URI for authorization and token exchange.
- Read `refresh_token_expires_in` from the authorization-code token response
  when Google supplies it. Store the calculated expiry with the connection and
  return it in connection status.
- Encrypt the refresh token at rest and keep access tokens in memory.

The complete URL is required. Unlike a bare authorization code, it lets MTL
Explorer validate the OAuth state.

### Why loopback and manual return

Google Desktop clients allow HTTP loopback callbacks, not arbitrary LAN or
remote server URLs. `localhost` / `127.0.0.1` refers to the browser's device,
which may differ from the MTL Explorer host. Detecting the GUI's browser URL
cannot bypass this restriction. The unused loopback address and manual paste
let NAS and remote installations connect without a local helper or HTTPS setup.

Automatic return to a remote server requires a Web application client with an
exactly registered HTTPS callback; non-loopback IP addresses are rejected.
See Google's [Desktop redirect rules](https://developers.google.com/identity/protocols/oauth2/native-app#redirect-uri_loopback)
and [Web redirect rules](https://developers.google.com/identity/protocols/oauth2/web-server#uri-validation).

### Token storage and recovery

The refresh-token encryption key is derived from the configured OAuth client
secret, which stays outside the database. This protects a database-only copy;
it does not protect against access to both the database and server secrets.
Changing the client secret makes existing ciphertext unreadable. Reauthorize
after changing it; mirrored records are retained. The access-token refresh
handler logs decryption failures and marks the connection for reconnection.
An unreadable token can also indicate corruption, so the UI must not claim a
client-secret change as the only cause.

### Advanced proxy callback

The optional Web-client callback accepts an unauthenticated GET because the
return from Google cannot carry the browser's MTL Explorer authorization
header. An authenticated request must first create the random state and
server-held PKCE verifier. The callback consumes that state once, within ten
minutes, before exchanging a code. Missing, expired, or replayed state cannot
change the connection. The manual completion endpoint remains authenticated.

## Reauthorization contract

The initial setup must explain that the user will normally reconnect every
seven days. MTL Explorer must then:

1. If Google reports a refresh-token expiry, show it as soon as setup finishes
   and use it to schedule an Admin action before authorization is lost. Do not
   invent an expiry when Google omits the field.
2. Detect an expired or revoked refresh token and set
   `RECONNECT_REQUIRED`. The timer may provide an earlier reminder, but an
   OAuth failure remains authoritative.
3. Classify an `invalid_grant` close to the recorded expiry as a likely
   Testing-mode expiration. Other causes, including revocation, remain
   possible and must not be hidden.
4. Show an action-needed notification using the existing Admin overview and
   Import & sync status patterns.
5. Provide a direct **Reauthorize** action and prepare a new Google consent
   link. Do not send the user through the complete setup assistant again.
6. Reuse the configured client ID and client secret. The user only completes
   Google consent and pastes the returned loopback URL.
7. Require a new refresh token when recovering from
   `RECONNECT_REQUIRED`; never fall back to the token known to be invalid.
8. Replace the stored token, clear any cached access token, clear the error,
   keep the local mirror and track associations, and start synchronization.

An expiry warning can say:

> This Google authorization expires on September 8 because the OAuth project
> appears to be in Testing. Reauthorize Google to keep syncing.

The supported action is **Reauthorize**. Moving the project to **In production**
is not supported.

Reauthorization must not delete or duplicate the connection, mirrored health
records, imported routes, or user-owned track data.

## Reliability invariants

- Resolve `users/me/identity` after authorization and persist its stable
  `healthUserId`. Refuse a different account when the mirror contains records,
  source metadata, synchronization audits/work, or retained route ownership.
- Use one time domain for both a Google window and its authoritative deletion.
  Exercise uses civil start time; physical records use instants; daily rollups
  use dates. Offset and daylight-saving changes must not move records outside
  the replacement window.
- Persist a rotated refresh token returned during access-token refresh while
  holding the same refresh lock.
- On `401`, or Google's structured `MISSING_OAUTH_SCOPE` response, invalidate the cached
  access token and retry once. Do not treat every `403` as stale authorization;
  distinguish missing scope, project policy, and disabled API.
- Persist pagination progress. Stop an incomplete window when a page token
  repeats or the configured page limit is reached. Never run authoritative
  deletion for an incomplete window.
- Only structured `INVALID_PAGE_TOKEN` responses restart a historical token
  traversal with smaller bounds. Other 400s remain request failures. Work retries
  are durable; exhausted ranges park without authorizing deletion or final marks.
- Validate every successful page before persistence: the root must be an
  object, collections must be arrays of objects, page tokens must be bounded
  strings, and a page must not exceed the requested result limit. A missing
  collection remains valid because ProtoJSON normally omits empty repeated
  fields; `{}` can therefore be a legitimate empty Google response and cannot
  be rejected as malformed.
- Honor both delta-seconds and HTTP-date forms of `Retry-After`, with bounded
  retry delays.
- Log job and phase lifecycle, bounded progress milestones, final counts, and
  durations at `INFO`. Keep page-level detail and repeated recoverable stack
  traces at `DEBUG`. Log recoverable exceptions once with safe context and log
  terminal failures at `ERROR` with the throwable.
- Report current-pass progress separately from the local mirror summary. The
  server calculates the exact unique row count and earliest/latest record time
  across both mirror tables. The database projection remains in the persistence
  package and is mapped to a separate web response DTO. Mirror-wide aggregate
  queries for status, per-type summaries, and sources are cached for five
  minutes while work is active, refreshed as soon as a new job completes, and
  invalidated when the mirror is deleted.


See [synchronization](synchronization.md) for work policies and checkpoints.
