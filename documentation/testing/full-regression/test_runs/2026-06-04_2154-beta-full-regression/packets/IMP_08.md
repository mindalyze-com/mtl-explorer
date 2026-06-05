# Packet: IMP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_08
- In scope: Statistics import-count verification after five public GPX files were imported.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 through IMP_07 terminal; baseline and post-import API summaries captured.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only comparison of existing baseline, import, API, and stats evidence plus packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared the zero-track baseline with the post-import API, geometry, indexer, and statistics evidence for the five staged public GPX files. | Statistics count increases by five unless a source file legitimately splits into multiple displayed tracks; any split must be mapped source-to-track. | Baseline API showed zero tracks; post-import summaries show TRACK_COUNT=5, five SUCCESS/COMPLETED_WITH_SUCCESS indexed files, five map geometry ids, and stats UI shows 5 TRACKS. Each staged GPX produced exactly one displayed track, so no split mapping was needed. | PASS | [assets/IMP_01-api-baseline.txt](../assets/IMP_01-api-baseline.txt); [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt); [assets/IMP_06-api-file-summary.txt](../assets/IMP_06-api-file-summary.txt); [assets/IMP_05-stats-after-reload.txt](../assets/IMP_05-stats-after-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-api-baseline.txt](../assets/IMP_01-api-baseline.txt) | Text/log evidence |
| [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) | Text/log evidence |
| [assets/IMP_06-api-file-summary.txt](../assets/IMP_06-api-file-summary.txt) | Text/log evidence |
| [assets/IMP_05-stats-after-reload.txt](../assets/IMP_05-stats-after-reload.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Evidence comparison | <1 minute |

## Handoff Notes

- Completed: IMP_08 is terminal; five source files map to five displayed tracks.
- Remaining unfinished coverage: Continue with IMP_09 aggregate totals and heatmap/statistics evidence.
- Blocked or not applicable: none.
- State left for the next packet: five imported public GPX tracks remain present.
