# Ingest issue acknowledgement plan

Status: implemented. Date: 11 October 2026.

Current contracts: [source issues architecture](../architecture/source-issues.md),
[Processing](../administration/maintenance/processing.md), and
[source review](../user/tracks/source-review.md). The sections describing current
behaviour below retain the pre-implementation design context.

## Problem

MTL Explorer reports GPS and media import problems, including files that cannot
be read or contain no usable data. Some problems are acceptable to the user,
but the same files can keep drawing attention after the user has acknowledged
them. Processing outcomes, GPS review decisions, and map notice dismissal do
not share one model.

### Current behaviour

| Mechanism | Meaning | Storage |
| --- | --- | --- |
| Map Acknowledge | Hide the current failed-file notice | Browser storage, scoped to server URL and account |
| Map Minimize | Close the explanation and leave the compact indicator | Component state |
| GPS Save use settings | Choose map/statistics inclusion and acknowledge current source findings | Server database |
| Media failed-file list | Show filenames and saved errors | Server database, without per-file acknowledgement |

The map acknowledgement survives ordinary logout and login in the same browser.
It has no expiry timer. Clearing local application data removes it; another
browser or device has its own acknowledgement. Processing, stale-map, offline,
and unavailable-status notices remain independent.

The map compares a saved token with the current GPS/media failure counts and
the latest failed-file update timestamps. This records a processing snapshot,
rather than a decision about the actual problems. Retrying the same bad file
can restore the notice. Fixing or removing one file can also restore the notice
for the remaining acknowledged files.

GPS review already compares source content and meaningful findings. Cosmetic
wording, ordering, and measurement rounding do not invalidate its decisions;
new defects can require review even when the source bytes are unchanged. This
review model does not control the map's processing-failure notice. A reviewed
GPS source can therefore remain a failed file needing attention on the map,
while an imported track with quality findings belongs to a separate queue.

Acknowledgement is also coupled to GPS use settings. An empty source should
not need a map/statistics exclusion choice merely to stop repeated notices.
Conversely, acknowledging a timing problem cannot make its measurements
reliable or rebuild a failed import.

The client retains a five-minute freshness snooze and dismiss handler without
a current UI caller. This adds an unused dismissal concept alongside the map
notice.

Settings → Processing derives its import **Needs attention** state from the raw
failed-file count. A user can inspect failures but cannot acknowledge them
there. Hiding the map notice therefore leaves the processing warning active.
The user needs an action that clears accepted source problems consistently in
both places, while preserving their processing outcomes and explanations.

Current references:

- [Map notice](../../mtl-client/src/components/map/MapDataStatusNotice.vue) and [map status resolution](../../mtl-client/src/components/map/mapDataStatus.ts).
- [Indexer summary](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/indexer/IndexerStatusService.java) and [failure timestamp query](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/db/repository/indexer/IndexerRepository.java).
- [GPS review persistence and finding identity](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/trackreview/SourceReviewRepository.java).
- [Freshness store](../../mtl-client/src/stores/dataFreshnessStore.ts) and [map loading methods](../../mtl-client/src/components/map/composables/useMapDataLoading.ts).
- [Processing progress and attention state](../../mtl-client/src/components/admin/IndexerStatusTab.vue) and [failed-file details](../../mtl-client/src/components/admin/FileIndexerFailures.vue).
- [Current processing guide](../administration/maintenance/processing.md) and [GPS review guide](../user/tracks/source-review.md).

### Current database storage

GPS decisions are stored in `track_review_decision`. Each save appends rows;
the latest applicable row for a source revision and section determines its
choice. This table currently combines use settings and acknowledgement.

| Column | PostgreSQL type | Current meaning |
| --- | --- | --- |
| `id` | `bigint`, generated primary key | Decision history order |
| `source_id` | `bigint`, foreign key to `indexed_file.id` | GPS source file |
| `source_hash` | `varchar(64)` | Assessed source content hash |
| `finding_hash` | `varchar(64)` | Semantic findings acknowledged by this choice |
| `section_index` | `integer` | Source section, or `-1` for an unsectioned source |
| `decision` | `varchar(24)` | `INCLUDE`, `EXCLUDE_MAP`, `EXCLUDE_STATISTICS`, `EXCLUDE`, or `LATER` |
| `decided_at` | `timestamp with time zone` | Time of the decision |

All columns are non-null. An index covers
`(source_id, source_hash, section_index, id DESC)`. There is no account column;
the choices apply to the shared library. `track_source_review` stores the GPS
assessment, diagnosis JSON, and combined review state. The map acknowledgement
has no database row; it remains in browser storage.

See the [existing table definitions](../../mtl-server/src/main/resources/db/changelog/changes/084.xml).

## Plan

Use one server-owned issue and acknowledgement model for GPS and media.
Acknowledge one file or all current issues through the same operation. Keep
processing outcomes and data-use choices separate from acknowledgement.

### Responsibilities

| Layer | Responsibility |
| --- | --- |
| Processing outcome | Record pending, imported, skipped, or failed work and its reason |
| Issue | Describe a meaningful problem, its source revision, severity, and affected features |
| Acknowledgement | Record that an account accepts leaving a particular issue as it is |
| Use settings | Control whether available tracks contribute to the map and statistics |

Acknowledgement does not change source files, processing outcomes, geometry,
measurement reliability, or map/statistics use. Use settings remain library
decisions; acknowledging issues is an account decision. Informational findings
remain visible without requiring acknowledgement.

Keep the existing GPS diagnosis engine, section inspection, and import evidence.
Generalise the issue contract and acknowledgement bookkeeping, rather than
adding a second acceptance mechanism only for failed indexed files.

### Database design

Add two tables: `source_issue` for GPS/media issues and
`source_issue_acknowledgement` for current account acknowledgements. Retain
`track_review_decision` for map/statistics use only. The SQL below describes
the proposed target schema; it is not an applied migration.

#### Source issues

One row represents one semantic defect in one source revision. A changed
revision or meaningful defect creates a different row; updating explanatory
wording or retry timestamps does not.

```sql
CREATE TABLE source_issue (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    source_id bigint NOT NULL
        REFERENCES indexed_file(id) ON DELETE CASCADE,
    source_revision varchar(80) NOT NULL,
    finding_hash varchar(64) NOT NULL,
    stage varchar(32) NOT NULL,
    reason varchar(64) NOT NULL,
    severity varchar(16) NOT NULL
        CHECK (severity IN ('INFORMATION', 'REVIEW')),
    section_index integer NOT NULL DEFAULT -1,
    message text NOT NULL,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at timestamp with time zone,
    UNIQUE (source_id, source_revision, finding_hash),
    CHECK (section_index >= -1)
);

CREATE INDEX source_issue_active_review_ix
    ON source_issue (source_id, id)
    WHERE resolved_at IS NULL AND severity = 'REVIEW';
```

`source_id` identifies the file through the existing indexer. Its GPS/media
index and location remain in `indexed_file`; do not duplicate them here.
`source_revision` is `sha256:<content hash>` for readable contents. For an
unreadable source, persist an `unreadable:<UUID>` token for the continuous
unreadable episode and reuse it across retries. It identifies that episode,
not proven file contents. Once the file can be read, resolve the unreadable
issue and assess its actual content revision.

Do not use `meta:<size>:<mtime>` as a content identity. Metadata can be unavailable
for unreadable sources, and changing modification time alone does not establish
new contents. Available metadata can remain explanatory evidence in `details`.

`finding_hash` identifies the semantic defect within that revision.
`details` holds structured evidence such as affected point indices, counts,
measurements, and affected features. The common issue table uses one normalized
severity: `REVIEW` requires acknowledgement; `INFORMATION` does not. The server
maps domain findings through its existing review policy. GPS warnings that
require review map to `REVIEW`; the assessment-limit exception maps to
`INFORMATION` while retaining its incomplete-assessment explanation. The
original GPS severities remain in its diagnosis evidence. This mapping must not
treat incomplete assessment as a healthy source. Keep meaningful domain changes,
including severity escalation, in `finding_hash` even when their normalized
severity is unchanged.

There is no separate `review_required` column. A meaningful change in normalized
severity belongs in the finding identity, so a newly reviewable defect cannot
inherit a silent acknowledgement. An empty-file informational skip has reason
`EMPTY_FILE` and severity `INFORMATION`.

An issue is active while `resolved_at` is null. Repeated assessment upserts the
same identity and updates its explanation and `last_seen_at`. Resolve absent
findings only after a complete assessment of their relevant stage. A failed or
limited assessment must not falsely resolve findings it could not check.

Normal source removal retains the existing `indexed_file` row with status
`REMOVED` and resolves its issues, preserving history. An explicit hard deletion
or database reset removes the associated issues and acknowledgements through
cascading foreign keys. This is distinct from clearing browser data.

#### Current account acknowledgements

Store only the current acknowledgement for each account and issue. A row means
acknowledged; no row means pending when the issue is active and reviewable.
Resolution belongs to `source_issue`, not to an account decision.

```sql
CREATE TABLE source_issue_acknowledgement (
    issue_id bigint NOT NULL
        REFERENCES source_issue(id) ON DELETE CASCADE,
    account_key varchar(255) NOT NULL CHECK (account_key <> ''),
    acknowledged_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (issue_id, account_key)
);
```

`account_key` comes from the canonical authenticated server principal, not from
a submitted username, browser identifier, JWT token, or login/session reference.
Current authentication uses a configured account rather than a persistent user
table, so use its canonical username without a session foreign key. Login case
variants must produce the same key. Renaming that account creates a new scope;
account renaming/migration is outside this change.

**Acknowledge** inserts a row with `ON CONFLICT (issue_id, account_key) DO NOTHING`.
**Reopen** deletes that row. Repeating either operation without an intervening
opposite action is harmless. Keep the original `acknowledged_at` on duplicate
inserts. No append-only acknowledgement history, request ID, or expected latest
decision protocol is required. The primary key supports the per-issue account
lookup; add further indexes only when query plans justify them.

The primary key prevents duplicate acknowledgements; it does not order opposite
user intentions. Concurrent Acknowledge and Reopen requests use ordinary database
write ordering. A delayed Acknowledge processed after Reopen can acknowledge the
issue again. The UI serializes opposite actions for the same issue while a
request is pending, and the API does not add automatic mutation retries.
Cross-device writes still follow server processing order. A stronger replay
guarantee would require extra state and is outside this design.

Keep the source/issue revision validation described below. It protects a
different requirement: an old request must not acknowledge a new defect or
source revision. Use the existing transactional source guards where needed;
do not introduce a separate lock-ordering protocol for acknowledgement.

There is no browser/device column, expiry column, or global accepted flag on
`indexed_file`. Account scope is within this installation's database. A file
with three reviewable issues gets three acknowledgement rows when acknowledged;
the UI still counts it as one file.

#### Shared demo account

Current authentication configures one login in
[WebSecurityConfig](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/web/security/WebSecurityConfig.java).
The [demo login response](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/web/services/auth/AuthController.java)
supplies those same credentials to demo visitors. Visitors using that login
therefore share one account: an acknowledgement by one visitor is visible to
the others.

Accept this shared behaviour for the current demo and clear the affected
acknowledgements whenever its demo data is reset. Keep Acknowledge available.
Separate visitor accounts or principals are a future authentication feature;
do not substitute session identifiers for the account key in ordinary use.

#### Map and statistics decisions

Keep `track_review_decision` as the shared-library use-setting history, with
this target shape:

| Column | PostgreSQL type | Planned meaning |
| --- | --- | --- |
| `id` | `bigint`, generated primary key | Use-setting history order |
| `source_id` | `bigint`, foreign key to `indexed_file.id` | GPS source file |
| `source_hash` | `varchar(64)` | Source content revision the setting applies to |
| `section_index` | `integer` | Existing source-section mapping |
| `decision` | `varchar(24)` | `INCLUDE`, `EXCLUDE_MAP`, `EXCLUDE_STATISTICS`, or `EXCLUDE` |
| `decided_by` | `varchar(255)` | Canonical account that saved the library setting |
| `decided_at` | `timestamp with time zone` | Time of the setting |

All target columns are non-null. Keep the existing latest-decision index and
add a check constraint for the four use choices. Remove `finding_hash` from
this table; acknowledgement lives only in `source_issue_acknowledgement`. **Review
later** leaves issues pending and writes no use-setting row. A whole-source
save retains the existing section mapping and writes choices for the affected
sections.

`decided_by` records the actor; it does not make use settings account-specific.
The latest row for `(source_id, source_hash, section_index)` still controls the
shared library. Existing track exclusion flags can remain projections of these
choices. Refactor `track_source_review.review_state` and
`gps_track.review_status` so they are not parallel authorities for an account's
acknowledgement. Keep GPS diagnosis JSON as assessment evidence, with the common
issue rows owning issue lifecycle and the acknowledgement rows owning review.

#### State lookup and examples

Derive the state for the authenticated account; do not save separate pending
or accepted counters:

| Issue row | Acknowledgement row for this account | Effective review state |
| --- | --- | --- |
| `resolved_at` is set | Present or absent | Resolved |
| Active, severity `INFORMATION` | Present or absent | No acknowledgement required |
| Active, severity `REVIEW` | Absent | Pending |
| Active, severity `REVIEW` | Present | Acknowledged |

For example, issue `101` is a GPS parsing failure for source `42`, revision
`sha256:<hash A>`. Synthetic current acknowledgement rows could look like this:

| `issue_id` | `account_key` | `acknowledged_at` |
| --- | --- | --- |
| 101 | reviewer | 2026-10-11 12:00:00Z |
| 101 | second-reviewer | 2026-10-11 12:02:00Z |

Issue `101` is acknowledged for these accounts; an account without a row still
sees it as pending. Reopen by `reviewer` deletes only `(101, reviewer)`; the
second account's acknowledgement remains. A retry with the same contents and
defect keeps issue `101`. If source `42` changes to revision `sha256:<hash B>`
and still fails, create a new issue row without inheriting issue `101`'s
acknowledgements. Older resolved findings retain their issue records and any
remaining acknowledgement rows, but past Acknowledge/Reopen actions are not
retained as an audit trail.

The pending-file count uses active reviewable issues with no acknowledgement
for the authenticated account:

```sql
SELECT COUNT(DISTINCT i.source_id)
FROM source_issue i
WHERE i.resolved_at IS NULL
  AND i.severity = 'REVIEW'
  AND NOT EXISTS (
      SELECT 1
      FROM source_issue_acknowledgement a
      WHERE a.issue_id = i.id AND a.account_key = :account_key
  );
```

Bulk requests carry the exact issue IDs and source revisions in the displayed
snapshot. Validate and write them transactionally; new or changed issues are
not covered by the acknowledgement.

### Issue lifecycle and identity

For each account, a reviewable issue is pending, acknowledged, or resolved.
Acknowledgement persists on the server across logout, later logins, browser-data
clearing, device changes, and server restarts. Another account retains its own
pending issues. There is no automatic expiry.

Use source identity, a content-based source revision, and a semantic defect
identity. Reuse the GPS finding identity approach: stage, reason, severity,
section, affected observations, and affected count where relevant. Adapt it to
individual issues so removal of one defect does not invalidate acknowledgement
of another.

Size and modification time can trigger discovery, but do not alone prove that
contents changed. Retry counters, processing timestamps, exception wording,
finding order, and cosmetic measurement changes must not identify a new issue.
Unreadable sources need an explicit revision representation without pretending
that their contents were successfully assessed.

| Event | Result |
| --- | --- |
| User acknowledges current issues | Those issue revisions become acknowledged for that account |
| Logout/login, map reload, restart, or another device | Acknowledged issues stay quiet |
| Retry or rescan finds the same defect in unchanged contents | Acknowledged issues stay quiet |
| Another file is fixed, removed, or changed | Unrelated acknowledgements stay valid |
| A particular problem disappears or its source is removed | That issue becomes resolved |
| Changed source contents still have a reviewable problem | The new source revision requires review |
| Assessment finds a new meaningful defect in unchanged contents | The new defect requires review; existing acknowledgements remain valid |
| User selects Reopen | That account must review the selected issue again |

A successful import resolves import failures that disappeared. It does not
automatically resolve unrelated GPS quality findings. Acknowledged and resolved
findings remain available with their explanations and current acknowledgement
timestamps. Reopen removes the acknowledgement row; no acknowledgement action
history is required.

### User flow

- Review provides **Acknowledge this file's issues** for GPS and media.
- The map provides **Acknowledge all current issues** for the issues represented
  by its current summary.
- Settings → Processing provides the same overall **Acknowledge all current
  issues** action, covering current GPS and media source issues. Each import
  group also offers acknowledgement for that group's issues.
- These actions preserve the findings and current use settings. Keep the term
  **Acknowledge** consistent; do not introduce a separate Accept action.
- Review offers Pending and Acknowledged filters, diagnosis links, **Reopen**,
  and **Retry** where applicable. Retry schedules processing independently of
  acknowledgement; it does not automatically reopen an unchanged issue.
- GPS retains separate whole-source and section map/statistics settings.
  Choosing Exclude does not implicitly replace issue acknowledgement.
- Processing reports progress and outcomes, including informational skips,
  exposes per-file acknowledgement, and links to the same Review entries.
- The map counts distinct files with pending reviewable issues, including
  quality findings in successfully imported sources. Multiple findings or
  affected calculations must not count the same source file repeatedly.
- Minimize closes the explanation while leaving the compact indicator.
  It makes no saved review decision.

Bulk acknowledgement must identify the source and issue revisions represented
by the displayed summary or list. File IDs alone are insufficient. Issues that
arrive or change while the request is in progress remain pending. Per-file
requests must also reject stale issue revisions rather than acknowledge unseen
findings. Repeated inserts or deletes are harmless without an intervening
opposite action; concurrent opposite actions follow database write ordering.

For example, acknowledge ten files with issues. An unchanged retry stays quiet.
Fixing three files stays quiet. If one new file has an issue, the map reports
one file needing review.

### Settings processing progress

Make **Needs attention** actionable. Show the number of files with pending
source issues and offer **Review issues** and **Acknowledge all current issues**
on the Processing page. The overall action covers the whole library's current
GPS and media issues, independently of map filters and list pagination. A GPS
or media group action covers only that index. File details provide the same
per-file action as Review.

For source-related attention, derive the warning state from the current
account's pending issues, not from raw failure totals. After acknowledgement,
clear the source attention badge and warning styling once its pending count
reaches zero. If pending issues remain or arrive later, keep **Needs attention**
with the updated count. When processing has finished with no pending issues,
show **Complete · N files acknowledged** if known issues remain; if work is
still running, retain its running state and progress.
Keep failed/skipped totals and their explanations visible as factual outcomes.
Acknowledge does not increase the successful-import count.

Calculations unavailable because of those same source issues must use the same
acknowledgement when deciding whether to request attention. They can continue
to show unavailable results and their reasons, without another persistent
warning asking the user to accept the same limitation. Current operational
failures keep their attention state and an appropriate recovery action.

Update Processing, map, and Review summaries together from the bulk response.
Only clear source attention after the server confirms the save. Disable
overlapping acknowledgement/Reopen actions during submission and show a save
failure without clearing warnings. No separate Processing dismissal state or
extra acknowledgement table is needed.

### Bulk acknowledgement API

Submit the complete selection in one request. Use the same endpoint for an
overall action, one import group, selected files, or a single file; only the
target array differs. The client must not call a per-file endpoint in a loop
or submit only the currently visible list page.

Provide a proposed snapshot endpoint:

```http
GET /api/source-issues/acknowledgement-targets
```

It returns the complete compact array of pending issue IDs and source
revisions, with counts from that same snapshot. An optional `index=GPS` or
`index=MEDIA` limits an import group action. The default includes all pending
GPS/media source issues for the authenticated account. Obtain the array in one
read when preparing the action; do not fetch every detail page or attach the
full array to every progress poll. Display the snapshot counts and submit its
targets without expanding the selection to later arrivals.

Example snapshot response, with content hashes abbreviated for readability:

```json
{
  "pendingFileCount": 2,
  "targets": [
    { "issueId": 101, "sourceRevision": "sha256:<hash A>" },
    { "issueId": 102, "sourceRevision": "sha256:<hash A>" },
    { "issueId": 203, "sourceRevision": "sha256:<hash B>" }
  ]
}
```

The first two issues can belong to one GPS file and the third to a media file.
Submit the full `targets` array to the proposed write endpoint:

```http
POST /api/source-issues/acknowledgements
Content-Type: application/json
```

```json
{
  "targets": [
    { "issueId": 101, "sourceRevision": "sha256:<hash A>" },
    { "issueId": 102, "sourceRevision": "sha256:<hash A>" },
    { "issueId": 203, "sourceRevision": "sha256:<hash B>" }
  ]
}
```

The server supplies `account_key` from `Authentication.getName()`. Deduplicate
the targets and match them to active `REVIEW` issues with the exact submitted
source revisions. Insert acknowledgements through a set-based
`INSERT … SELECT … ON CONFLICT DO NOTHING` in one transaction. Do not perform
one repository lookup/save or one source reassessment per target. Large input
can use bounded internal SQL batches while retaining one client submission
and one transaction.

Return newly acknowledged, already acknowledged, and stale/resolved target
counts, plus refreshed pending-file counts and GPS/media summaries for the
current account. Stale or resolved targets are not acknowledged; other valid
targets can still succeed. A database failure rolls back the whole write.
If a per-file selection is entirely stale, show that its issues changed and
refresh instead of claiming success. Issues arriving after the snapshot
remain pending and appear in the returned summary.

This needs no persisted bulk-operation record or request ID. The target array
defines the selection, and the existing primary key handles duplicate inserts.
The opposite-action ordering tradeoff remains the one documented for the
current-state table.

### Skipped files and operational failures

After the existing stable-file checks, classify zero-byte sources as
informational skips. Show their filenames and reasons in Processing without a
map review notice. Process them again when their contents change.

Skipped describes an outcome; severity determines whether review is needed.
Do not silently classify damaged or unsupported contents, parsing failures,
or files without usable GPS observations as acceptable. Record their structured
reason, show the issue once, and allow acknowledgement.

Keep pattern exclusions distinct from content-based skips. Do not reuse
`EXCLUDED` for both meanings. Keep any retained data from an earlier source
revision identified as earlier data; acknowledging or skipping its replacement
does not establish a successful current import.

Job and Google Health failures retain their operational progress, retries, and
recovery actions in Processing. Offline, unavailable-status, preparation, and
stale-map indicators remain separate from accepted source problems. A file
acknowledgement must not hide a current service failure.

### Implementation sequence

Implement the common backend model first, then the complete client flow. Ship
the consolidated behaviour after both phases pass their checks.

#### Phase one server model and APIs

1. Define the common server issue contract, typed reasons, semantic identity,
   normalized severity, and lifecycle. Classify source-content problems
   separately from unexpected processing or service failures. Keep diagnosis
   and attention policy on the server.
2. Add account-scoped acknowledgement persistence and APIs through the existing
   repository and review services, using the [database design](#database-design).
   Guard writes with source and issue revisions; support per-file, bulk, and
   Reopen operations. Add the [snapshot and bulk array API](#bulk-acknowledgement-api)
   with set-based writes and returned summaries. Use the current-state table
   without an acknowledgement audit trail or replay protocol. Reset shared demo
   acknowledgements with its data. Use normal Liquibase schema changes without
   legacy backfill or migration-only endpoints.
3. Connect GPS source review and media failures to the common model. Separate
   GPS acknowledgement from use settings and replace the old acknowledgement
   bookkeeping. Preserve section choices, measurement restrictions, and the
   existing assessment-limit policy. Populate common issues during normal
   imports and explicit reassessment; do not add an automatic GPS backfill.
4. Add the narrow zero-byte skip policy and accurate outcome totals. Provide
   pending and acknowledged summaries and list filters through the common
   account-aware policy. Export the live OpenAPI schema and regenerate the
   TypeScript client using the normal server-first workflow. Verify backend
   identity, lifecycle, account scope, and snapshot guards.

#### Phase two client flow and cleanup

1. Update the map, Review, and Processing flows together using the generated
   APIs. Keep use settings separate from acknowledgement and count each source
   file once. Add overall, group, and per-file actions to Processing, and derive
   its source-related attention from pending issues. Submit each complete bulk
   selection once and update all summaries from the response. Serialize
   opposite UI actions while their request is pending; show save failures
   without adding automatic mutation retries.
2. Remove the browser findings-token comparison and
   `processingFindingsAcknowledged` storage. Remove the unused freshness snooze,
   dismiss handler, storage key, and obsolete snooze tests. Retain freshness
   detection, reload behaviour, and their tests.
3. Verify the complete lifecycle and demo flow. Update processing, GPS review,
   media, map, and architecture documentation when the behaviour is implemented.
   Regenerate the website and installed guide from their sources.

### Acceptance checks

- Per-file and bulk acknowledgement produce the same saved result. Neither
  changes use settings, source contents, or metric support.
- The overall action on the map and Settings → Processing covers all pending
  GPS/media source issues in its snapshot, including files beyond the visible
  list page. Group actions cover only their index.
- Each mass action sends one full target array in one write request, with no
  per-file or pagination submission loop. Server writes use set-based SQL or
  bounded internal batches, rather than per-issue repository calls.
- After a successful save, Processing clears acknowledged source attention,
  and map/Review counts agree with it. Failed/skipped totals remain factual,
  running work retains its progress, and correlated unavailable calculations
  do not demand another acceptance of the same source limitation.
- A failed bulk transaction clears no warning and commits no acknowledgement.
  Changed or newly arriving issues remain pending; returned counts show the
  actual result, including stale targets.
- The same account stays quiet after normal logout, clearing browser data,
  later login, another device, and server restart. Other accounts remain
  independent.
- Unchanged retries and rescans, cosmetic diagnosis changes, and removal of
  other issues do not restore acknowledged notices.
- Changed contents or a new semantic defect require review even when the total
  issue count is unchanged. Remaining acknowledged defects stay quiet.
- Issues arriving during bulk acknowledgement and stale per-file requests
  cannot be silently acknowledged. Duplicate inserts keep one acknowledgement
  row and its original timestamp; repeating Reopen leaves it absent.
- Reopen affects only the current account. Concurrent opposite actions follow
  database write ordering, with no stronger replay guarantee. Login case
  variants share the canonical account key; login/session references do not
  determine acknowledgement ownership.
- Demo visitors using the configured login share acknowledgements. Resetting
  demo data clears its acknowledgements without affecting another installation.
- Incomplete assessments do not resolve findings they could not check.
  Source removal retains resolved issue history; explicit database deletion
  removes dependent rows consistently.
- Corrected or removed sources resolve their corresponding issues. Imported
  GPS quality findings remain pending until acknowledged or actually resolved.
- Stable zero-byte GPS and media files appear as informational skips without a
  map review notice. Changed contents are processed again. Damaged and
  unsupported contents remain reviewable.
- Pending counts agree across the map, Review, and other review summaries and
  count each source file once. Technical outcome totals remain accurate.
- Normalized severity preserves the existing GPS review policy, including the
  assessment-limit exception. A domain severity escalation changes its finding
  identity even if normalized severity remains `REVIEW`.
- Processing, freshness, offline, and service-failure indicators remain accurate
  independently of source acknowledgement.

Use public or synthetic fixtures. Run backend checks in the development
container and relevant client tests for these contracts.
