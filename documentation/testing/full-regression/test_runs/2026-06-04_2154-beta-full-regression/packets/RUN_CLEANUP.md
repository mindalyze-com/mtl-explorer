# Packet: RUN_CLEANUP

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: RUN_CLEANUP
- In scope: Finalization gate, target stack shutdown, container verification, and disposable directory removal.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Finalization gate passed with all 174 coverage IDs terminal.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Stop only this run's MTL Explorer compose stack and remove only this run's disposable server directory.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_CLEANUP | Ran the finalization gate, stopped the mtl-explorer compose stack with docker compose down from the disposable install directory, verified no matching MTL Explorer containers remained, and removed the disposable run directory. | Cleanup stops installed quick-install containers, leaves unrelated Docker resources untouched, removes the disposable install directory, and records cleanup evidence. | Finalization gate passed. docker compose down removed app, db, brouter, location-search containers and mtl-explorer_default network. docker ps listed no matching MTL Explorer containers afterward. The disposable directory /root/mtl-regression-2026-06-04_2154-beta-full-regression was removed. | PASS | [RUN_CLEANUP-cleanup-summary.txt](../assets/RUN_CLEANUP-cleanup-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [RUN_CLEANUP-cleanup-summary.txt](../assets/RUN_CLEANUP-cleanup-summary.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Finalization gate | <1 second |\n| Compose down and directory removal | 12 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
