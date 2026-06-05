# Packet: DEL_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_02
- In scope: Wait for automatic delete processing or trigger Rescan GPS if needed.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01 terminal; two source files removed from watched folder.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only API polling and, if needed, documented Rescan GPS; packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_02 | Polled tracks, data freshness, and indexer status after the watched-folder deletion. | The app either processes the deletion automatically or a documented Rescan GPS action processes it; indexer/state reflect two removed source files. | Automatic processing was sufficient. The first API poll showed three remaining tracks, the two deleted filenames absent, data freshness tracks/index revisions advanced, and the GPS indexer reported total=5, completed=3, removed=2. | PASS | [assets/DEL_02-delete-processing-wait.txt](../assets/DEL_02-delete-processing-wait.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_02-delete-processing-wait.txt](../assets/DEL_02-delete-processing-wait.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Delete-processing poll | <1 second after file deletion |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
