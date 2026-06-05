# Packet: RUN_CLEANUP

## Scope

- Coverage source: `documentation/testing/full-regression/workflow/resumable-workflow.md`
- Coverage ID or run packet: RUN_CLEANUP
- In scope: Finalization gate confirmation, compose shutdown, container verification, and disposable remote directory removal.
- Out of scope: Global Docker pruning or removal of unrelated host resources.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP through ERR_02 terminal.
- Required app/data state: Finalization gate passed.
- Required browser context: Not required.

## Allowed Mutations

- Allowed: Stop the installed quick-install stack, remove its disposable remote directory, and keep local report/evidence in the run folder.
- Not allowed: Remove unrelated containers, images, volumes, Docker installation, or non-run directories.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_CLEANUP | Confirmed finalization gate PASS, ran `docker compose down` from the remote compose directory, verified no matching MTL Explorer containers remained running, and removed `/root/mtl-regression-2026-06-01_1727-beta-201`. | Cleanup stops the quick-install stack and removes only the disposable install directory. | Gate passed with 171 terminal coverage IDs. Compose removed app, database, BRouter, location-search containers and the compose network. Running-container grep returned no matches. Disposable run directory removal verified with `removed`. | PASS | [assets/RUN_CLEANUP-cleanup.txt](../assets/RUN_CLEANUP-cleanup.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_CLEANUP-cleanup.txt](../assets/RUN_CLEANUP-cleanup.txt) | Finalization gate result and remote cleanup verification. |

## Timings

| Step | Timing |
|---|---:|
| Compose shutdown | ~11 s |
| Directory removal and verification | <1 s |
| Cleanup completed | 2026-06-02T01:27:50+02:00 |

## Handoff Notes

- Completed: RUN_CLEANUP terminal as `PASS`.
- Remaining unfinished coverage: None.
- Blocked or not applicable: None.
- State left for the next packet: Remote quick-install stack stopped; disposable remote run directory removed; local run folder contains packets/assets for final report.
