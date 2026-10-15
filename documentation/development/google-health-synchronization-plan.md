# Google Health synchronization implementation plan

Status: implemented and verified locally 10 October 2026.

Simplify automatic historical imports to ordinary batch upserts and permanent
day completion records. Reuse the existing audit table, record identities,
durable work, and ownership guards. Keep recent synchronization behavior.

The [synchronization architecture](../architecture/health/synchronization.md)
describes the implementation. This plan remains the authority for the historical
import contract and its acceptance checks.

## Scope and synchronization lanes

| Lane | Range and schedule | Record writes |
| --- | --- | --- |
| Recent fast | Last physical 24 hours, every cycle, with existing civil/date envelopes | Complete-chunk upsert and replacement |
| Recent full | Non-final part of the seven-day recent window; due 24 hours after completion, resumed while unfinished | Complete-chunk upsert and replacement |
| Historical | Eligible unmarked days before the recent cutoff, newest first, down to the configured floor | Batch upsert, then one successful audit mark per day and stream |

Keep current defaults: a 30-minute cycle, a seven-day recent window, a 24-hour
fast window, a 24-hour full-pass interval, and a history floor of `2010-01-01`.
Preserve explicit installation settings and endpoint limits.

Historical records are treated as fixed. Future upstream changes are outside
the automatic synchronization contract and must not drive this implementation.
A completed historical day is never automatically imported again. Recovery is
explicit user maintenance, such as resetting the mirror and importing afresh.

## Logical days and completion records

The logical unit is a native day for one account, data type, representation,
calendar policy, and key policy. API pages, query subdivisions, and database
batches are execution details. Their sizes can change without changing which
days are complete. Retain the existing key and calendar policy versions; a
write optimization must not rekey records or reopen completed days.

Retain the existing native time rules and half-open `[start, end)` bounds:

| Stream | Day ownership |
| --- | --- |
| Physical samples and intervals | UTC day of the sample or interval start |
| Exercise | Civil start day |
| Sleep | UTC end day; viewing still groups sessions by start day |
| Daily summaries and rollups | Native date or aggregation day |

Keep physical instants, civil components, and dates distinct. Use existing
calendar policies, completion eligibility, civil envelopes, and microsecond
precision. Recent work cannot commit below its current cutoff; historical work
cannot finalize a day that is still recent-owned.

Use `google_health.history_audit` as the completion authority. Its existing
partial unique index allows one active successful record per scoped day.

| Outcome | Meaning |
| --- | --- |
| `DATA` | Every required query and page succeeded; at least one valid record was returned for the day and all writes are committed |
| `EMPTY` | Every required query and page succeeded and returned no records |
| Failed or absent | The day is incomplete |

Determine `DATA` versus `EMPTY` from that day's current successful traversals,
including returned records already stored. Zero new inserts or updates does
not mean `EMPTY`. Earlier failed or restarted traversals do not determine the
outcome. `EMPTY` describes the query result; it neither asserts that no local
rows exist nor authorizes their deletion. Failed attempts may have audit
entries, but never count as completion.

Gaps between measurements are valid. Completion means that every required
query and page succeeded, not that every instant has a measurement. An empty
page with a continuation token is not a completed query.

A backward watermark is only a cached summary of contiguous completed days.
For each stream, start at the newest eligible day and stop at the first
incomplete day. It never replaces the day audit lookup. Do not infer completion
from record timestamps, counts, or old cursors.

## Historical import flow

1. Select or resume the newest eligible incomplete day. Before requesting it,
   check the scoped audit. Skip `DATA` and `EMPTY` permanently during automatic
   synchronization, including after restart or downtime.
2. Reuse durable work with frozen query bounds and an ownership generation.
   Query subdivisions cover the whole day without gaps or overlaps. Keep one
   active historical owner per account and logical stream.
3. Fetch and validate one page. Normalize its records once, resolve sources
   inside the guarded transaction, and write them in ordinary batches.
4. Commit the page records, next-page checkpoint, query-validation state,
   counts, and historical rotation together. Verify account, current ownership,
   day eligibility, and absence of a successful audit at every commit. Follow
   every continuation token. Earlier committed pages survive a later failure
   or interruption.
5. After all required subdivisions and pages succeed, use a short guarded
   transaction to write the successful day audit and complete its work.
   Completion performs no mirror scan, record stamping, or absence deletion.

Retain bounded empty-range batching where supported. Each covered day still
receives its own completion record. A shared request or write batch never
becomes the completion unit. Shared requests include only contiguous eligible
days and exclude completed, waiting, parked, or separately owned days. Older
eligible days can still progress beyond a failed gap; that gap stays incomplete.

## Historical record writes

Start with batched `INSERT … ON CONFLICT` through the existing repositories.
Insert all validated returned records. If the identity already exists, reuse
its row and update content only when it differs. Identical retries must produce
the same stored result without rewriting unchanged rows.

A row already stored by an earlier import is an upsert conflict. Conflicting
duplicates within the current query are a validation failure; these are
different cases.

Remove these operations from the automatic historical path:

- Per-record traversal stamps and writes made solely to record query membership.
- Absence sweeps and historical deletion or replacement of missing identities.
- Separate database scans solely to calculate changed-record counts before
  writing; derive counts from persistence results where needed.
- Repeated validation and mapping of the same page.

Completion and retry safety do not depend on per-record stamps. Retain traversal
IDs in work/checkpoint state for request resumption and ownership checks.

Keep the existing input and identity safeguards:

- Accept a Google name as identity only when it is a complete
  `users/{user}/dataTypes/{type}/dataPoints/{point}` resource matching the type.
  A collection path cannot identify a record.
- Preserve the current canonical fallback for nameless records. Different
  observations at the same time remain distinct, including when source
  metadata is missing or identical.
- Reconciled and rollup identities remain independent of page and batch sizes.
- Collapse identical duplicate identities. Reject conflicting content for the
  same identity within one query, including across pages and resumed work.
- Reject invalid payloads, times, and out-of-range records before their page
  commits. Source caches remain scoped and are invalidated on rollback/reset.

For named and other content-independent identities, keep the identity/fingerprint
state needed for cross-page validation in durable query work. Commit it with the
page checkpoint, restore it on resume, and discard it when that traversal is
restarted, revoked, or completed. Identities that already include the canonical
observation fingerprint need no separate conflict state. Keep this state within
the existing query limits; it contains no second copy of record payloads and
requires no mirror membership stamps or scans.

Use ordinary batching against the existing mirror tables. Separate record
staging tables and a storage redesign are outside this change.

Heart-rate indexes separate named from nameless identity. Complete Google names
keep a unique `(connection_id, identity_key)` index, so a correction can move one
row in time. Nameless keys already include the physical time and canonical
observation; their unique index is `(connection_id, sample_time, identity_key)`.
Recent and maintenance lookups pass the sample time for nameless records.
This keeps writes near the current time range while preserving key values and
logical uniqueness. Unused mirror fingerprint indexes are removed.

## Interruption and failure handling

A transaction failure rolls back its records and checkpoint together. On
restart, resume the last committed checkpoint. A replayed page is safe because
writes use the existing unique identities.

A reset, reload, or retry that replaces ownership revokes stale generations.
Old workers cannot commit pages, advance checkpoints, or write successful day
marks. The final audit and work completion are atomic; a failed finalization
leaves the day incomplete.

Keep existing bounded retries, waiting/parked states, and token handling.
A structured `INVALID_PAGE_TOKEN` restarts the affected subdivision under a
new traversal and smaller bounds where possible. Do not reuse a token after
changing its request. Repeated tokens or unrecoverable requests leave work
incomplete. Parked work does not prevent older eligible days from progressing;
contiguous coverage still stops at the gap.

Budget yields, cancellation, quota pauses, and authorization pauses are not
work failures. Page memory and write batches remain bounded.

## Existing behavior to preserve

Recent fast/full passes continue to fetch complete chunks, skip unchanged
content, and replace absent identities within their exact queried bounds.
They retain frozen full-pass positions, retries, cutoff handoff, and due times.
Successful historical days remain excluded even if the recent window widens.

Keep the serialized worker, fixed-rate schedule, lane reservations, stream
rotation, shared request limiter, and durable cooldown. Scheduling budgets
control admission of another request or finalization. An admitted page saves
under the independent database transaction timeout; it must not repeatedly
roll back merely because its scheduling slice expired.

Explicit range/all reload remains user-requested maintenance with its existing
replacement contract. Keep that maintenance path separate from automatic
historical imports. Only explicit maintenance supersedes successful day marks;
retrying incomplete work does not reopen completed days. Full reset clears
mirror records, audits, work, failures, passes, and caches under the guard.
Reload intent is a range audit with outcome `RELOAD_REQUESTED`, never a successful
day mark. Historical work freezes its `replace_missing` mode from that intent;
the request also survives a recent-to-history handoff.

Same-account reauthorization preserves marks and work. Empty audits and route
ownership still protect account identity; another account requires an explicit
reset. Zero mirror rows never imply a reset.

Route exports remain separate from record completion. Keep route ownership,
published files, and track associations. Automatic synchronization does not
delete published routes, and adding location permission does not reopen
historical days.

## Status and measurement

Expose per-stream completed days, active day, pending/parked work, attempts,
retry time, and contiguous coverage. Include empty completed days. Use indexed
audit lookups and cached coverage; status polling and page commits must not
scan the full audit table or mirror.

Report fetched records and persisted changes separately. Derive changes from
write results, never from fetched counts. If an exact count is unavailable,
report that rather than adding a count query or inventing a value. Keep legacy
stamp/deletion counters at zero for automatic history; do not reuse them to
classify day outcomes. Cycle counts cover commits in that cycle; cumulative
work counts survive restart.

Use the [import simulator](testing/google-health-simulation.md) to compare the
old and new implementations with identical datasets, storage, and settings.
Use a fixed dataset for historical imports; correction/removal cases belong to
recent synchronization or explicit maintenance. Compare expected records and
day audits as well as elapsed time. Measure:

- Historical import time per day, database/transaction time, rows per second,
  page counts, and commit counts.
- Active processing time separately from total elapsed time, including
  scheduling waits and retries.
- Recent fast/full completion while historical work is pending.
- Indexed work selection and status cost with a long history floor and gaps.

Simulator transaction time covers work inside transactions. Page normalization
occurs before automatic historical writes, so compare total elapsed time too.
The simulator does not establish Google network latency or NAS disk throughput.
Use existing NAS measurements for those operational comparisons. Do not claim
a throughput target before measurement.
An empty-mirror benchmark does not reproduce scattered index reads in a large
existing mirror with a small database cache. Verify that workload separately;
an individual lookup speedup is not a full-import speedup.

Log useful phase durations and counts without health values, payloads,
tokens, credentials, or account/device/point identifiers.

## Implemented changes

1. Automatic history has its own persistence path, reusing validation, canonical
   identities, source resolution, and the existing repository. Recent replacement
   and explicit maintenance retain their separate behavior.
2. Batch upserts omit stamps, absence sweeps, pre-write count scans, and duplicate
   mapping. Retry and duplicate-conflict safeguards remain in place.
3. Days finish from durable query/page state and valid returned counts, using the
   existing audit table and unique index. Status retains day-level coverage.
4. Correctness and performance use the existing simulator and Java checks in the
   development container.
5. Architecture and user documentation describe this import contract.

Use Liquibase only for required schema changes. No legacy backfill, identity
migration, automatic reset, new repair feature, or live deployment is included.
Change 104 adds maintenance mode and bounded query-validation/count state to
existing work tables, plus an index for reload intent. Record tables, key policy,
calendar policy, and successful audit uniqueness are unchanged.
Change 105 replaces the global nameless heart-rate identity index with the
time-based unique index and removes unused fingerprint indexes. It does not
rekey records, backfill values, or reopen completed days.

## Acceptance checks

- A dense, paginated day stores every expected record and one successful audit.
  A later automatic run makes no historical request for that completed day.
- Valid empty days complete once and stay complete after restart. Measurement
  gaps do not prevent completion.
- A day whose returned records already exist is `DATA`, not `EMPTY`.
- Interruptions before a page commit, after it, and during finalization preserve
  consistent records/checkpoints. Resume adds no duplicate records or marks.
- Invalid records, conflicting duplicates, repeated/expired tokens, and failed
  pages leave the day incomplete until a valid traversal succeeds. Cross-page
  conflict checks survive restart. Revoked workers cannot commit.
- Parked gaps remain visible while older days progress. The backward watermark
  stops at the first gap.
- Canonical identities preserve named records and differing same-time nameless
  observations. Fresh imports with different page or batch sizes produce equal
  records and day marks; existing completed days are never reopened by resizing.
- Automatic history performs no traversal stamp updates or absence deletion.
  Completion cost depends on work/audit state, not all stored rows in the day.
- Recent replacement, explicit reload/reset, account protection, route retention,
  quota handling, and budgeted continuation retain their behavior.
- The benchmark produces the same expected records and completed days with lower
  historical persistence cost; timings distinguish processing from elapsed time.
