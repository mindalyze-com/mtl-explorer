# Diagnostic logging

MTL Explorer stores client diagnostics and existing server operation events in
`system_log`. Source, severity, topics, stack trace, structured JSON context, build
ID, and login reference are nullable. Older rows retain their original values and
unspecified classifications. The additive Liquibase change does not backfill or
rewrite them; the existing timestamp type, creation-date behavior, and indexes remain.

Use the shared singleton or its `logSanitizedError` / `warnSanitized` delegates:

```ts
clientLogger.error('Media loading failed', error, {
  topic1: 'media', topic2: 'viewer', topic3: 'load',
});
clientLogger.warn('Map tiles temporarily unavailable');
```

Metadata accepts `stackTrace?: string | null`. Omission extracts the original
error stack; null suppresses extraction; a string supplies the trace. Sanitization
retains frames and line breaks, including minified and Axios frames. The console
receives the resolved record. The logger works before stores, router, or mounting.
Vue, window, and promise rejection handlers share it and register browser listeners
once. Optional startup diagnostics add no browser reporting listeners. Console
redaction remains local.

Normal startup and sign-in transitions remain informational and are not queued
as problems. Startup warnings and errors take the original error and structured
context as separate arguments. Context-only warnings have no exception type,
description, or stack. See [frontend error-handling rules](../development/frontend.md#errors-and-diagnostics).

Startup warnings and errors report independently of the `startupDiag` toggle;
the toggle controls verbose informational output. Map rendering and graphics-context
failures, navigation errors, and selected media, routing, analysis, review, health,
and administration failures use the same service. Cancellation, stale requests,
normal teardown, speculative prefetch, and browser capability fallbacks stay local.
Health and source-review reporting uses the original transport error before private
response details are read for the UI.

After local console logging, the client immediately starts persistence in a
separate IndexedDB database through Dexie. Capture never uploads. Records carry
a stable UUID, original occurrence time, build, and original login reference;
credentials are never stored in the queue. Pending identical records within
60 seconds of their last occurrence consolidate into one record with an occurrence
count and first/last times. Every occurrence is still logged to the console.
Once an upload is attempted, its payload is immutable; later repetitions form
another record so a retry always carries the same ID and content.

Insertion and housekeeping share a transaction. Keep the newest 250 records,
including sent and rejected records, and at most 4 MiB of serialized diagnostic
payloads. Older unsent records can be evicted. Pending captures in memory have
the same bounds while persistence is running. Oversized diagnostics retain a
bounded rejected record locally; their full resolved record remains in the console.
There is no legacy-data backfill.

An independent sender checks every second and wakes on startup, login,
connectivity changes, and return to the visible app. It sends retained pending
records in insertion order, one request at a time, at most 25 per run. An IndexedDB
lease coordinates tabs and expires after an abandoned sender. An idle check uses
the pending-status index without loading retained payloads or writing delivery
state. The rolling budget
is 100 upload attempts per 60 seconds, persisted across reloads and shared between
tabs for the same backend. Failed attempts count; exceeding the budget defers
records without discarding them.

Network, timeout, and server failures leave records pending and pause the queue
with increasing retry delays, capped at 60 seconds. HTTP 429 honors Retry-After.
HTTP 401 pauses until the login reference changes. Permanent invalid
requests, including HTTP 403 for an invalid historical login reference, become
rejected and do not block later reports. Successful server
acknowledgment sets sent. The sender uses current credentials, a five-second
request timeout, and a generated API configuration without auth redirects or
reporting interceptors. Delivery failures never report themselves.

Reports captured with an account remain scoped to that account and backend.
Account comparisons follow the case-insensitive login rules.
Pre-login diagnostics can be delivered after login and bind to that account on
first attempt. Credentials-only logout retains the queue; forget-everything logout
stops delivery and clears persistent and memory records, including other tabs. If IndexedDB opening or
writing fails, the bounded memory queue continues delivery for that page session.
It cannot survive reload; the persistent database is retried on the next startup.
Browser storage can also be cleared or evicted. Page timers cannot deliver while
the app is closed; reopening resumes the stored queue.

`POST /mtl/api/logs/client` requires the existing authenticated API rule and
returns 204 after insertion. Its explicit DTO requires nonblank message and build
ID, accepts optional WARN/ERROR severity and nullable stack, and excludes entity
ID, date, source, and login reference. The controller assigns CLIENT, the server storage time (`createDate`),
and the validated current JWT login reference. Client `occurredAt` remains the
original event time; repeated reports also carry `lastOccurredAt` and
`occurrenceCount`. The optional original login reference is checked against the
currently authenticated account. A unique client event ID makes retries idempotent,
including concurrent retries. New fields are nullable for existing rows and callers. Invalid or oversized input is rejected;
stacks are never truncated to fit.
On client submission the server computes a nullable, indexed `stackFingerprint`
using SHA-256 over the sanitized captured stack. It removes leading exception
header/message lines, normalizes line endings and surrounding line whitespace,
and keeps every remaining nonblank line in order, including all frames and their
file, line, and column locations. V8 `at ...` and Firefox/Safari `function@...`
frame starts are recognized. Missing or unrecognized stacks have no fingerprint.
Message, detail, context, build, severity, and login references are excluded.
The log list and detail APIs expose the fingerprint; reports remain separate and
existing retry/counting behavior is unchanged. Browser formatting and changed
bundle locations can produce different fingerprints. Existing rows are not backfilled.
Source identifies the reporting side. A browser reporting an HTTP failure still
produces a CLIENT event; this does not establish the failure's root cause.

Limits: 128 KiB request, 4,096 message characters, 16,384 detail characters,
65,536 stack characters, 8 KiB context, 255 characters per topic, and 128 build
characters. Safe context is limited to error type/code, HTTP method/status,
route name, Vue information, source/line/column, browser/PWA mode, package version,
component, and operation. Do not supply tokens, cookies, passwords, URL queries,
private IDs, request/response bodies, health/location data, or raw application state.
The client reporting service and ingestion controller redact URL credentials and
absolute or relative URL queries while retaining stack line and column locations.

Server writers use the existing service and independent insert transactions.
Known classifications and emitting build identity are populated. Exception stacks
have their own field. Best-effort server writers catch metadata, save, and commit failures;
ingestion returns 503 on persistence failure and uses ordinary server logging.
Each accepted client report also writes one bounded, single-line `Client problem`
summary at INFO to the server file log. It contains the stored log ID, client
severity, topics, a short sanitized message, exception type, and exception description.
The client supplies the type in `context.errorType` and the description in `detail`
(normally the original exception message). The file summary uses only their first
line, limited to 64 and 256 characters respectively; absent values are unspecified.
Full stacks, detail, and context remain in the database; the client severity does
not set the server file-log level.

See [stored log administration](../administration/maintenance/logs.md).

## Client login reference

Each successful login creates a `ClientLoginRef` and a signed JWT. The reference
identifies that login in application logs and optional analytics. It cannot
authenticate a request on its own; the valid JWT is the credential. After
validating the JWT, the server checks the login's active state and expiry.
Signing out revokes that login, so its JWT no longer grants access.

| Contract | Name |
| --- | --- |
| Java and TypeScript field | `clientLoginRef` |
| JWT claim, database column, and application log field | `client_login_ref` |

The login record is stored in `web_client_login`; request and browser environment
logs link to it by `client_login_ref`. See [database relations](database.md#client-login-and-request-logs).

## Stored event API

`GET /mtl/api/admin/system-logs` and `GET /mtl/api/admin/system-logs/{id}`
require authentication. List pages default to 50 rows, with a maximum of 200,
ordered newest first with ID as the date tie-breaker. Event time uses the
client's original occurrence time when present, falling back to server creation
time. Null classifications appear as **Unspecified**. Stored content is rendered
as text.

Retention deletes one bounded batch per scheduled run, based on the server
creation date. Cleanup failures use ordinary server logging. A schema rollout
preserves existing rows and leaves new fields null.
