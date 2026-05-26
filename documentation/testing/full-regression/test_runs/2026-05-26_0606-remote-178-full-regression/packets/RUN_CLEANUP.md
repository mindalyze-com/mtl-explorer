# Packet: RUN_CLEANUP

## Scope

- Coverage source: `documentation/testing/full-regression/retest-instructions.md`
- Coverage ID or run packet: RUN_CLEANUP
- In scope: Finalization gate, compose stack shutdown, container verification, disposable directory removal.
- Out of scope: Global Docker pruning or unrelated resource cleanup.

## Prerequisites

- Required previous coverage IDs or run packets: All frontend-regression coverage IDs terminal.
- Required app/data state: Finalization gate passed.
- Required browser context: Not required for cleanup.

## Allowed Mutations

- Allowed: Stop the quick-install compose stack and remove the disposable install directory.
- Not allowed: Remove unrelated containers, images, volumes, or directories.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_CLEANUP | Ran the finalization gate, `docker compose down` in the disposable compose directory, verified no quick-install containers/port listener remained, and removed the disposable parent directory. | Cleanup starts only after gate PASS; installed stack stops; disposable directory is removed; unrelated Docker resources are left alone. | Gate passed with 168 terminal IDs. Compose removed the app, brouter, db, location-search containers and network. Verification showed 0 `mtl-explorer-*` containers, no disposable directory, and no listener on port 18080. | PASS | `assets/RUN_CLEANUP-cleanup.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/RUN_CLEANUP-cleanup.txt | Gate and cleanup verification output. |

## Timings

| Step | Timing |
|---|---:|
| Compose shutdown and directory removal | ~12s |
| Post-cleanup verification | <1s |

## Handoff Notes

- Completed: RUN_CLEANUP passed.
- Remaining unfinished coverage: none.
- Blocked or not applicable: none for cleanup.
- State left for the next packet: No next packet; final report assembly complete after cleanup status is written.
