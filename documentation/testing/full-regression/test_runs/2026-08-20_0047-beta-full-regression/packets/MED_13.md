# Packet: MED_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_13
- In scope: Persisted activity media rows, capture-time order, positions, and provenance across browser reload and app restart.
- Out of scope: Detailed provenance/database separation, covered by MED_14 and MED_15.

## Prerequisites

- Required previous coverage IDs or run packets: MED_05 and MED_06.
- Required app/data state: Track 100013 and its original eight correlated media fixtures.
- Required browser context: Track Details > Media for MTL Regression Media Track.

## Allowed Mutations

- Allowed: Reload the browser and restart only the disposable app container.
- Not allowed: Reindex, edit, or recalculate the activity/media dataset.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_13 | Record the eight-row timeline and marker state, reload the page, restart the disposable app, reload again, and compare UI/database state. | The same persisted rows, positions, origins, and capture-time order remain. | All eight row strings, marker accessible origins, marker classes, and rendered positions matched exactly after both reload and restart. Read-only SQL returned the same eight selected correlations in capture-time order. | PASS | [assets/MED_13-persistence.txt](../assets/MED_13-persistence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_13-persistence.txt](../assets/MED_13-persistence.txt) | Baseline order, exact reload/restart comparisons, startup result, and post-restart read-only correlation rows. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact row strings, marker accessibility/classes/positions, and database rows are linked above.

## Timings

| Step | Timing |
|---|---:|
| Baseline and page-reload comparison | 2 min |
| App restart to started state | 11.342 s |
| Post-restart browser and database comparison | 2 min |

## Handoff Notes

- Completed: Exact persisted timeline comparison across page reload and app restart.
- Remaining unfinished coverage: None for MED_13.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Disposable app is healthy after restart; Track 100013 Media tab is open with the unchanged eight-row baseline.
