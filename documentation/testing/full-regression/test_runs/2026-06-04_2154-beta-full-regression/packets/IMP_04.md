# Packet: IMP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_04
- In scope: Confirm all five source files reach completed state, freshness changes, no unexpected GPS index failures appear, and background jobs settle.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_03 live indexing completed.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only API/log verification.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_04 | Checked tracks/get, data-freshness, indexer status, and app logs for Duplicate Detector and Exploration Score after import. | Five source files complete successfully; data freshness changes from baseline; no GPS index failures; Duplicate Finder and Exploration Score settle. | All five GPX tracks have `loadStatus=SUCCESS`; freshness token advanced from baseline tracks/index/geometry revisions 0 to tracks=30/index=15/track_geometry=30; GPS indexer reported 5 completed and 0 failed; DuplicateDetectorJob and ExplorationScoreJob completed for five tracks. | PASS | [assets/IMP_03-index-final.txt](../assets/IMP_03-index-final.txt); [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt); [assets/IMP_01-api-baseline.txt](../assets/IMP_01-api-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-index-final.txt](../assets/IMP_03-index-final.txt) | Text/log evidence |
| [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) | Text/log evidence |
| [assets/IMP_01-api-baseline.txt](../assets/IMP_01-api-baseline.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Background jobs settled | about 63 seconds after copy |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
