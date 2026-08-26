# Packet: TBS_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_08
- In scope: Statistics after the required five-GPX import and after deleting two imported files.
- Out of scope: Every map removal surface, completed by revisiting MAP_04.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_09, TBS_07.
- Required app/data state: Full same-run dataset; full imported set no longer required by later checks.
- Required browser context: Statistics Overview and visible freshness Reload.

## Allowed Mutations

- Allowed: Move exactly two public GPX fixtures out of the disposable watched folder.
- Not allowed: Delete original/local private tracks or unrecoverable source data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_08 | Compare post-import Stats, move two GPX fixtures, wait for live deletion, reload, and compare Stats. | Counts/totals decrease and no stale deleted totals remain. | Five-import totals were populated; tracks 100002/100003 deleted; refreshed result changed to 7 tracks and 821 km with both names absent. | PASS | [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt); [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt) | Watcher/indexer deletion and post-delete totals. |
| [assets/IMP_09-totals.txt](../assets/IMP_09-totals.txt) | Five-import comparison state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible totals and server evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| Recoverable file move and watcher wait | 3 min |
| Frontend Reload and Statistics comparison | 2 min |

## Handoff Notes

- Completed: Required import/delete Statistics transition.
- Remaining unfinished coverage: None for TBS_08.
- Blocked or not applicable: None.
- State left for the next packet: Smart Base result synchronized to seven tracks; deleted GPX copies remain recoverable in disposable data/logs quarantine.
