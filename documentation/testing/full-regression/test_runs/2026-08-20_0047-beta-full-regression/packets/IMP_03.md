# Packet: IMP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_03
- In scope: Wait for GPS indexing; trigger Rescan GPS only if live watching does not react.
- Out of scope: Background job settlement and freshness reload.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02.
- Required app/data state: Five new files in the watched folder.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: Wait/refresh status; use Rescan GPS only if watcher fails.
- Not allowed: Add more tracks during this five-file completion check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Refresh Admin Processing; inspect live watcher and per-file ingest logs; decide whether manual rescan is needed. | All five reach completed state; manual rescan is recorded only if needed. | GPS reached done 5/5. Live watcher saw all five CREATE events and each file completed SUCCESS with IDs 100000-100004. No rescan was needed. | PASS | [assets/IMP_03-indexing.txt](../assets/IMP_03-indexing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-indexing.txt](../assets/IMP_03-indexing.txt) | Admin status, watcher events, IDs, per-file SUCCESS, and timing. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; Admin DOM status is recorded in the linked text asset.

## Timings

| Step | Timing |
|---|---:|
| GPS ingest completion | ~13 s in server logs; 55 s to recorded browser observation |

## Handoff Notes

- Completed: Five-file indexing finished via live watch without rescan.
- Remaining unfinished coverage: None for IMP_03.
- Blocked or not applicable: None.
- State left for the next packet: GPS done 5/5; downstream processing jobs were still running at the first refresh.
