# Packet: RUN_CLEANUP

## Scope

- Coverage source: `documentation/testing/full-regression/retest-instructions.md` cleanup section.
- Coverage ID or run packet: RUN_CLEANUP.
- In scope: Finalization gate, evidence audit, remote stack shutdown, disposable-directory removal, final report assembly.
- Out of scope: Global Docker pruning, unrelated containers/images/volumes, and recording SSH credentials.

## Prerequisites

- Required previous coverage IDs or run packets: All coverage IDs through ERR_02 terminal; finalization gate passed.
- Required app/data state: Quick-install stack running from `/root/mtl-full-regression-2026-06-20_2114-beta-178-full-regression`.
- Required browser context: Not required.

## Allowed Mutations

- Allowed: Stop the compose stack from the disposable install directory and remove only that directory.
- Not allowed: Remove unrelated Docker resources or record SSH credentials.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_CLEANUP | Ran the finalization gate after all required coverage IDs were terminal. | Gate prints `Finalization gate: PASS` before report/cleanup. | Gate passed: `Finalization gate: PASS (175 coverage IDs terminal)`. | PASS | [assets/RUN_CLEANUP-evidence-audit.txt](../assets/RUN_CLEANUP-evidence-audit.txt) |
| RUN_CLEANUP | Audited packet asset references, WebP sizes, text sizes, packet status rows, and disallowed `.log` assets. | Evidence audit passes with no missing links and no oversized assets. | Audit passed: 177 packet files, 455 asset files, 1,358 packet asset references checked, zero missing assets, zero oversized WebP/text files, zero `.log` assets. | PASS | [assets/RUN_CLEANUP-evidence-audit.txt](../assets/RUN_CLEANUP-evidence-audit.txt) |
| RUN_CLEANUP | Attempted and rechecked SSH cleanup using the supplied setup access note and key-based access against the disposable install directory. | SSH access succeeds, `docker compose down --remove-orphans` stops the quick-install stack, project containers are gone, and the disposable directory is removed. | BLOCKED: the target rejected the supplied setup access note after the earlier forced password rotation (`Permission denied, please try again.`). A password recheck on 2026-06-21 still returned SSH exit code 5; key-based SSH also returned exit code 255 (`Permission denied (publickey,password)`). The app URL still returned HTTP 200, so stack shutdown and directory removal could not be verified. | BLOCKED | [assets/RUN_CLEANUP-remote-cleanup.txt](../assets/RUN_CLEANUP-remote-cleanup.txt); [assets/RUN_CLEANUP-remote-cleanup-recheck.txt](../assets/RUN_CLEANUP-remote-cleanup-recheck.txt); [assets/RUN_CLEANUP-key-ssh-recheck.txt](../assets/RUN_CLEANUP-key-ssh-recheck.txt); [assets/RUN_CLEANUP-app-reachability-recheck.txt](../assets/RUN_CLEANUP-app-reachability-recheck.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_CLEANUP-evidence-audit.txt](../assets/RUN_CLEANUP-evidence-audit.txt) | Finalization/evidence audit result. |
| [assets/RUN_CLEANUP-remote-cleanup.txt](../assets/RUN_CLEANUP-remote-cleanup.txt) | Non-secret SSH cleanup failure output. |
| [assets/RUN_CLEANUP-remote-cleanup-recheck.txt](../assets/RUN_CLEANUP-remote-cleanup-recheck.txt) | Non-secret SSH access recheck output. |
| [assets/RUN_CLEANUP-key-ssh-recheck.txt](../assets/RUN_CLEANUP-key-ssh-recheck.txt) | Non-secret key-based SSH access recheck output. |
| [assets/RUN_CLEANUP-app-reachability-recheck.txt](../assets/RUN_CLEANUP-app-reachability-recheck.txt) | Remote app reachability recheck after cleanup failed. |

## Screenshot Evidence

No screenshot evidence is required for cleanup; text evidence records the gate/audit and SSH failure.

## Timings

| Step | Timing |
|---|---:|
| Finalization gate | < 1 s |
| Evidence audit | < 1 s |
| SSH cleanup attempt | ~4 s |
| SSH cleanup recheck | ~4 s |
| Key-based SSH cleanup recheck | < 1 s |

## Handoff Notes

- Completed: Finalization gate passed; evidence audit passed.
- Remaining unfinished coverage: none.
- Blocked or not applicable: Remote stack shutdown and disposable-directory removal are blocked until the current root SSH credential for `178.104.209.132` is provided.
- State left for the next packet: `report.md` has been created with `RESULT: FAIL` because cleanup is not verified. Resume RUN_CLEANUP with the current SSH credential, then stop the compose stack from `/root/mtl-full-regression-2026-06-20_2114-beta-178-full-regression`, remove that directory, verify containers are stopped, update RUN_CLEANUP/report.md, and set the run complete.
