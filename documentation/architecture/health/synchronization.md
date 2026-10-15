# Google Health synchronization

Google Health is a read-only local mirror. One serialized worker runs fixed-rate
cycles, normally every 30 minutes. Busy ticks are skipped. Each cycle has a new
sync ID and a deadline one minute before the next nominal tick. Manual sync,
reload, and retry use the same guard, budgets, request limiter, and durable cooldown.

Scheduling deadlines stop new requests. Complete batches save under the separate
database timeout, even across a stream or cycle deadline. Records, checkpoints
and historical rotation commit atomically before yielding; batch sizes are
unchanged. Historical finalization is a separately admitted transaction. A cycle
may overrun while its current batch finishes; busy ticks still create no new job.

| | Recent | History |
| --- | --- | --- |
| Ownership | Non-final days from the seven-day cutoff onward | Eligible unmarked days before the cutoff, down to the configured floor |
| Work | Last physical 24 hours first; frozen full-window pass due 24 hours after completion | Newest unmarked day first; bounded empty ranges may widen |
| Commit | Validate a complete chunk before upsert and exact-range replacement | Commit batch upserts and page checkpoints; finalize each logical day from completed work |
| Failure | Durable waiting/skipped cells; expired cells hand over to history | Durable waiting/parked parents; revoked workers cannot commit or finalize |

Both lanes have reserved time and may borrow unused capacity. Pending full and
reload work has a positive reservation inside recent time. Route exports have a
bounded turn inside that reservation. Fast-first work does not create extra jobs
or continuation chains. A completed full pass records its next due time; another
pass starts at its new cutoff rather than the old pass's end.

Temporary provider/network errors and transient database failures retain `WAITING`
work. From the fourth failure, the normal sync interval doubles per failure with
up to 20% added jitter, capped at `work-retry-max-delay` (six hours). Validation
and other non-recoverable failures retain the four-attempt skipped/parked limit.
Routes use the same policy; a failed route without a retry time needs explicit
retry. Quota cooldowns, budget yields and cancellation consume no work attempt.
Full recent passes advance past temporary cells in slow retry and finish with
errors, retaining their waiting gaps. Fast refreshes and later full passes retry
those gaps when eligible, so one failing stream cannot block new shared passes.
Reload parents inherit the cell's failure state and retry time without counting
another failure while the cell waits. Committed historical pages survive retries.
Unrecoverable pagination-token errors retain their immediate parking after token
recovery fails. Successful work clears its active error and retry time; earlier
failed-attempt audits remain history until the local mirror is deleted.

Physical instants, civil components, and calendar dates retain their native query
keys. Civil queries cover UTC−12 through UTC+14 at both ends. Date queries cover
whole native days, including the upper civil date. Requests and checkpoints use
microsecond precision. Exercise ownership uses civil start; sleep ownership uses
UTC end day. Sleep charts still group whole sessions by their start day in the
viewing time zone. A rejected upper envelope is a failure, never an empty success.

Named points retain Google identity, including heart-rate samples. Nameless raw
identity includes canonical observations and source metadata; differing same-time
observations survive. Nameless reconciled and rollup keys use native intervals.
Identical duplicates collapse; conflicting duplicates invalidate a complete query.
Historical pages are normalized once before their guarded transaction resolves
sources and writes bounded multi-row `INSERT … ON CONFLICT` batches. Unchanged
rows are not rewritten. Automatic history performs no membership stamping,
pre-write changed-count scan, missing-record sweep, or completion-time mirror scan.
Write results supply changed counts; stamp and deletion counts stay zero.

Named heart-rate records use a partial unique index on connection and identity;
timestamp corrections still update one row. Nameless identities include their
physical time and content, so their partial unique index starts with connection
and sample time before identity. Recent and maintenance lookups include that
time. Record keys and logical uniqueness stay unchanged. The time prefix avoids
scattered reads across the whole history during an import. Mirror fingerprint
indexes are no longer maintained; lookups use identity and native time.

Content-independent identities retain a bounded identity/fingerprint map in
`history_child.query_fingerprints`, committed with the page checkpoint. Resumed
queries restore it; restarted, revoked, and completed queries discard it. Nameless
raw identities already include content and need no separate conflict map.

Historical data is treated as fixed. One successful `history_audit` record per
account, stream, representation, calendar policy, key policy, and native day is
the completion authority. All required queries and pages must succeed before
the audit and work completion commit together. Valid returned counts distinguish
`DATA` from `EMPTY`; an unchanged existing record still counts as data. Measurement
gaps are valid. An empty result does not delete local rows. Bounded empty-range
queries still produce a separate completion record for each day.

Each cycle keeps a search position per stream and reuses its selected day when
creating work. The position moves backward only after that selected work and
its day audits commit. Completing older work resumed from another cycle does
not move it past newer gaps. A completed stream needs one gap search per cycle;
saved retries remain eligible after the search is exhausted. Positions reset
every cycle, and ownership guards still reject work revoked by reload. Audits
remain the completion authority. The cycle log's `historyGapSearches` counts
these gap queries, separately from provider requests.

Automatic sync skips successful days permanently, including after restart or a
wider recent window. Explicit range/all reload supersedes matching marks and
revokes overlapping work without pre-deleting records. A durable `RELOAD_REQUESTED`
audit records the requested range; new historical work sets `replace_missing`
and retains maintenance stamping and replacement, including recent-to-history
handoff. Request audits do not count as completion. Unrequested parked portions
retain their state under new owners. Retry releases incomplete work without
reopening successful days. Reset clears mirror, audits, work, failures, passes,
ownership, and caches. Empty audits and retained owners protect account identity.

Route ownership is separate from mirror points. Valid exports use atomic file
publication and keep their indexing associations. Recent replacement and explicit
reload update absent exercise presence while retaining its file, owner, and track. Track replacement also
updates absent owners. Adding location access exports stored missing routes
without reopening history. Automatic synchronization never removes published
route files; file deletion is confined to confirmed administration actions.

Status separates cycle counts from cumulative pass/traversal counts. It reports
fast bounds and completion, frozen full positions and due time, skipped/handed-over
chunks, parked ranges, and contiguous history finalized back to the first gap.
The Processing screen separates Recent, History, and Routes, with searchable
data-type disclosures and persistent expansion across cycle transitions. Growing
cycle totals use indeterminate activity; incomplete or paused work cannot appear
as a completed refresh. Mirror totals and history coverage carry snapshot times. Coverage snapshots are
bounded and cached; status polling does not reload all audits each time.

Shared client count timelines animate confirmed count changes, settle completed
cycles, and reset for each new sync ID. Active Data & Records, Processing, and service
views share one status request every eight seconds. Reopening a view reuses the
same in-memory snapshot and animation timeline.

Overview opens with only the grouped chart query. It reads any existing shared
status without requesting or polling it. Expanding Latest values loads the
per-type date bounds and latest measurements; successful results are reused
until the screen closes or a shared sync update makes them stale. Closing the
section discards pending results and prevents follow-up requests. Overview does
not fetch catalogue counts for its footer.

Health screen loaders track their own requests. Shared sync updates refresh
Overview charts and expanded latest values. Data & Records refreshes records,
summaries, and sources without invalidating an in-flight status retry. Closing
a screen invalidates all of its pending results.

Record list and detail responses expose an optional typed `measurement` with a
required numeric `value` and `unit`, plus optional `minimum` and `maximum`.
Values use the chart units. The server derives `valueSummary` from that same
measurement and preserves numeric zero. Unsupported or unavailable measurements
omit the typed value. OpenAPI defines the response and generated client types.
List queries project only measurement operands for the selected page. The full
provider `payload` remains available in record details for diagnostics.

The heart-rate covering-index migration drops a leftover index before retrying
its concurrent build. A recorded successful original migration keeps its index;
its original checksum remains valid.

There is no legacy backfill or identity migration. Successful audit marks stay
authoritative. An incomplete checkpoint without query-validation state restarts
its subdivision under new ownership without deleting committed records. Legacy
route columns remain inactive.

The [implementation plan](../../development/google-health-synchronization-plan.md)
contains the acceptance gate. Real-account density, upper-envelope acceptance,
first-seen delays, and comparative request/byte costs still need measurements;
synthetic checks do not establish a production freshness or speed guarantee.
See [authorization](authorization.md) and the [user guide](../../user/health/google-health.md).

## Chart grouping

The grouped metrics endpoint supports `AUTO`, `SECOND`, `DAY`, `WEEK`, `MONTH`,
and `YEAR`. `SECOND` accepts one local calendar day using the same inclusive
start date, exclusive end date, and viewing time zone as the calendar groupings.
This includes 23-hour and 25-hour daylight-saving days. The overview labels
`SECOND` as **Intraday**.

Heart rate reuses the repository's time buckets with a one-second interval.
Timestamped weight, body fat, body temperature, and blood glucose readings are
grouped by second after conversion to display units. Both retain average,
minimum, and maximum in `detailedPoints`, with the selected day's summary in
`buckets`. Daily values are not substituted for missing samples. The same
chart component draws the time series and breaks a line where the gap between
readings exceeds three times the series' median reading spacing, so devices that
record every few seconds or minutes still draw a line. Readings with no neighbor
within that spacing are drawn as markers.

The overview owns one time window and one marked time for all intraday graphs.
The time window control, drag selection, and pinch update that shared window
without another metrics request; each graph applies it to its axis without
rebuilding its series. Selecting a point marks that time on every graph, and
each graph shows its nearest reading within the gap spacing. The window and mark
are kept when expanding a graph and reset when a different day or grouping
loads. A daily measurement graph offers the selected day as an intraday view.

## Setting constraints

The settings are listed in [Google Health setup](../../administration/google-health.md#synchronization-settings).
The fast window must be at least the sync interval and no longer than the
physical recent window. Duration settings must be positive. `recent-window`
covers at least two whole days, `cycle-deadline-margin` stays smaller than the
interval, and `history-budget-fraction` stays strictly between zero and one.
Explicit `history-start-date` values are preserved. Request spacing is one
second across pages, probes, retries, and route exports. Provider cooldowns
persist across restarts. Civil offsets are limited to UTC−12 through UTC+14;
the earlier parser's ±18-hour range is no longer accepted.

`cursor-overlap`, `initial-window`, and `daily-reconciliation-history` are
retired. Daily/monthly reconciliation schedules and automatic continuations are
removed. The default history floor changed from 1970 to 2010; it is a
configured limit, not Google's earliest available date. The current layout
targets fresh mirrors; existing checkpoints and route state from earlier
development builds are not migrated or silently discarded.
