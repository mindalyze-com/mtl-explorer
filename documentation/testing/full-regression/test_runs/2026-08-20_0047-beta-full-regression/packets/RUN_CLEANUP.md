# Packet: RUN_CLEANUP

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: `RUN_CLEANUP`
- In scope: Finalization-gate confirmation, disposable Compose teardown, exact-directory removal, endpoint/resource checks, browser cleanup, SSH closure, and evidence retention.
- Out of scope: Global Docker pruning, image removal, and unrelated server resources.

## Prerequisites

- Required previous coverage IDs or run packets: `RUN_SETUP` and all 235 frozen coverage IDs terminal.
- Required app/data state: Disposable installation at `/root/mtl-regression-2026-08-20_0047` after the final coverage packet.
- Required browser context: Run tabs still open only for final closure.

## Allowed Mutations

- Allowed: Stop and remove the disposable Compose project, its volumes and network; remove the exact disposable directory and run-specific temporary file; close run tabs and SSH.
- Not allowed: Global Docker pruning, image removal, unrelated server changes, or removal of the local run evidence folder.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_CLEANUP | Confirm the gate, run `docker compose down --volumes --remove-orphans`, remove the exact disposable directory and temporary capture, verify project resources and endpoints are absent, close run tabs, and close SSH. | The disposable installation and its resources are gone, the app endpoint is closed, evidence is retained, and unrelated resources are untouched. | The gate passed with all 235 coverage IDs terminal. All four project containers and the project network were removed; no matching containers, volumes, or networks remain. The exact directory is absent, both local and public app URLs refuse connections, run tabs and SSH are closed, and local evidence remains. | PASS | [assets/RUN_CLEANUP-verification.txt](../assets/RUN_CLEANUP-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_CLEANUP-verification.txt](../assets/RUN_CLEANUP-verification.txt) | Gate, teardown, resource, endpoint, browser, SSH, and retention verification. |

## Screenshot Evidence

No screenshot is useful after the app endpoint and run tabs are closed; compact command evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| `docker compose down --volumes --remove-orphans` | About 10 s |
| Full cleanup and verification | About 35 s |

## Handoff Notes

- Completed: Gate confirmation, Compose teardown, directory and temporary-file removal, resource/endpoint checks, browser cleanup, SSH closure, and evidence retention.
- Remaining unfinished coverage: None.
- Blocked or not applicable: None for cleanup.
- State left for the next packet: Cleanup is verified; final report assembly and audit can proceed from the completed packet set.
