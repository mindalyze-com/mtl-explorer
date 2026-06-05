# Packet: IMP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_03
- In scope: Wait for indexing to finish after five-GPX import and record whether Rescan GPS was needed.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02 copied five public GPX files into the watched folder.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only API/log polling while the live watcher processes files.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Polled authenticated tracks/freshness/indexer APIs and captured app logs after copying the five GPX files. | Live file watching indexes the files or a documented Rescan GPS is triggered and recorded. | Live watcher detected CREATE for all five GPX files; all five imported tracks appeared with `loadStatus=SUCCESS`; indexer status reported total=5, pending=0, completed=5, failed=0; no Rescan GPS was needed. | PASS | [assets/IMP_03-index-final.txt](../assets/IMP_03-index-final.txt); [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-index-final.txt](../assets/IMP_03-index-final.txt) | Text/log evidence |
| [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Copy-to-success indexing | about 15 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
