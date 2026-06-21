# Packet: IMP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_03
- In scope: Wait for indexing to finish after the watched-folder import, and record whether manual Rescan GPS was needed.
- Out of scope: Per-track UI verification after refresh.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02.
- Required app/data state: Five public GPX files are present in watched import folder.
- Required browser context: authenticated desktop context available.

## Allowed Mutations

- Allowed: Poll read-only installed APIs; trigger Rescan GPS only if live watching stalls.
- Not allowed: Add more files or delete imported files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Polled track count, simplified tracks, GPS indexer status, jobs, and freshness after copying the five GPX files. | Indexing finishes; if file watching does not react, Rescan GPS is triggered and recorded. | Live watcher reacted without manual Rescan GPS. First poll already showed 5 tracks and GPS indexer `completed: 5`, `failed: 0`; background jobs settled by poll 16 after 42.6 seconds. | PASS | [assets/IMP_03-index-monitor.txt](../assets/IMP_03-index-monitor.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-index-monitor.txt](../assets/IMP_03-index-monitor.txt) | Polling log showing automatic indexing and job settlement. |

## Screenshot Evidence

No screenshot required; UI evidence for the settled state is in IMP_04.

## Timings

| Step | Timing |
|---|---:|
| Import indexing and job settlement | 42.6 s |

## Handoff Notes

- Completed: IMP_03.
- Remaining unfinished coverage: IMP_04 onward; DAT_03 imported mapping pending IMP_06.
- Blocked or not applicable: none.
- State left for the next packet: five imported GPX tracks exist server-side; client view is stale and shows the freshness banner.
