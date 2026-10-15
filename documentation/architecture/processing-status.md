# Processing status and dependencies

`GET /api/status?include=processing` collects domain-owned job reports. The
[status collector](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/status/JobStatusService.java)
contains no workflow prerequisites or job-specific counting queries. Each
[ProcessingJob](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/status/ProcessingJob.java)
owns its report, declared dependency edges, and prerequisite evaluation.
Execution and reporting call the same evaluation. Shared import barriers live
with their job consumers in `ImportPrerequisites`, rather than in status collection.

## Live reports

Existing outcome counts and percentages remain available. Additional fields
provide the server-selected state, `work.ready`, `work.waiting`, `work.retrying`,
`blockedBy`, `nextRunAt`, `nextRetryAt`, `unit`, and `progressKnown`.
Ready includes eligible items currently executing; it is not a count of worker
threads. The three work counts partition pending work. Source limitations and
attention counts remain separate from execution state.

The shared policy chooses Running while a batch executes; otherwise Queued
when any work is ready, Retrying when any work is deferred for retry, Waiting
when any work awaits prerequisites, and Complete when no work remains. An
active whole-job gate also reports Waiting before upstream discovery has
established any pending count. Unknown totals use `progressKnown=false`.

Blockers identify an index, job, lock, or user action, with whole-job or item
scope and Automatic or User-action resolution. Lock blockers report the
current owner. Classification awaiting duplicate checks and energy awaiting
classification are ordinary waits. A completed classification with no activity
requires user action. Exploration applies its gate to the entire pending count
without querying point eligibility for every pending track. Media duplicate
changes are retriggers, rather than a whole-job blocking prerequisite.

A request memoizes shared import facts. Existing database aggregates gain
filtered counts where possible. Current gates are evaluated on every poll;
last batch results are not used as a stale prerequisite cache.

## Execution and scheduling

`BackgroundJobRunner` distinguishes ownership of a continuation chain from
execution. A domain batch signals its start after gates and work selection;
execution is cleared in a `finally` block. Heavy batches obtain the shared
resource lock before entering this execution scope. Typed batch outcomes
preserve blockers or retry times while controlling continuation scheduling.

`nextRunAt` describes a scheduled check or an immediate continuation. It is
registered at application startup and is not a promised start time.
An occupied resource lock or scheduler can delay execution. Runtime markers
remain in memory; work and retry deadlines remain in the database. Counts and
markers can cross a commit boundary, so an executing batch may briefly have
zero pending items.

## Dependency metadata and GUI

Each enabled provider advertises Gate, Input, or Retrigger edges. The collector
returns them once at the top level of Processing, with an opaque
`dependenciesVersion`. A caller can pass that version in later requests;
unchanged edges are omitted while live reports are still returned. The client
keeps the cached graph and replaces it when its version changes.

The GUI displays server-selected states with distinct icons, visible blocker
reasons, linked rows, and a compact dependency section inside each job's Details. Attention is an
independent badge. Servers without the added state fields retain the previous
client presentation. Indexer rows and map/routing services keep their existing
state contracts.

Task counts play confirmed samples eight seconds behind the shared status clock,
with a local render tick every 400 ms. Each row uses one completed count to keep
its summary, pending count, ready work, details, and meter in step. Stable totals
and outcomes allow playback; changed totals, outcomes, blockers, execution state,
or decreasing completed counts reset it immediately. Finished work also settles
immediately. Reduced motion shows the latest sample; hidden views pause their
render tick, and reopening reuses the shared timeline. Polling remains unchanged.

Synthetic Google Health advertises its import, duplicate detection, activity
classification, and energy gates through its own
provider. It appears under Imports & sync, reports preparation before day totals
are known, publishes committed day progress, and reports automatic retry times.
