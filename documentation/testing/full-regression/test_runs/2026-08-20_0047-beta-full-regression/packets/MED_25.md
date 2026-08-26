# Packet: MED_25

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_25
- In scope: Production live-watcher deletion service, pre-delete requeue, queue survival after activity deletion, recalculation fallback, and cleanup.
- Out of scope: Per-item worker failure/defer behavior, covered by MED_26.

## Prerequisites

- Required previous coverage IDs or run packets: MED_24 cleanup.
- Required app/data state: Empty correlation queues and disposable PostGIS access.
- Required browser context: Track 100013 Media tab for final baseline equality only.

## Allowed Mutations

- Allowed: One synthetic media row, two fully synthetic GPX activities, and one bounded after-delete audit trigger/table.
- Not allowed: Delete an original regression activity or retain fixture/audit rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_25 | Import primary/fallback activities, establish selected/alternate correlations, move primary from watched storage, inspect bounded after-delete audit and final projection, then clean all fixture rows. | Production deletion requeues correlated media; queue survives activity deletion; recalculation falls back to another activity, EXIF, or no position. | Live watcher and GPXStoreService deleted track 100024. After-delete audit found `track_delete` work while track/correlation were absent. Worker selected fallback 100025 and moved TRACK_INTERPOLATED resolution to 47.101/8.101. Cleanup restored exact baseline. | PASS | [assets/MED_25-production-delete-fallback.txt](../assets/MED_25-production-delete-fallback.txt); [assets/MED_25-primary.gpx](../assets/MED_25-primary.gpx); [assets/MED_25-fallback.gpx](../assets/MED_25-fallback.gpx); [assets/MED_25-seed-media.sql](../assets/MED_25-seed-media.sql); [assets/MED_25-cleanup.sql](../assets/MED_25-cleanup.sql) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_25-production-delete-fallback.txt](../assets/MED_25-production-delete-fallback.txt) | Production logs, correlation states, after-delete audit, fallback result, and cleanup. |
| [assets/MED_25-primary.gpx](../assets/MED_25-primary.gpx) | Fully synthetic selected activity deleted through the watcher. |
| [assets/MED_25-fallback.gpx](../assets/MED_25-fallback.gpx) | Fully synthetic fallback activity. |
| [assets/MED_25-seed-media.sql](../assets/MED_25-seed-media.sql) | Isolated camera-time media seed. |
| [assets/MED_25-cleanup.sql](../assets/MED_25-cleanup.sql) | Bounded fixture and audit cleanup. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact production logs, transaction audit, and UI equality are linked above.

## Timings

| Step | Timing |
|---|---:|
| Primary import and correlation | 8 s |
| Fallback import and correlation | 8 s |
| Production deletion detection and service | 8 s |
| Fallback recalculation | < 1 s after delete commit |

## Handoff Notes

- Completed: Selected/alternate setup, production deletion, queue-survival audit, fallback recalculation, and full fixture cleanup.
- Remaining unfinished coverage: None for MED_25.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: No MED_25 watched files or database/audit rows; original media/resolved/selected counts 8/8/8; work queues empty; UI exact baseline.
