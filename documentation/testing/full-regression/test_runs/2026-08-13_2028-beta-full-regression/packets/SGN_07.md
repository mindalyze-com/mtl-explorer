# Packet: SGN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SGN_07.
- In scope: force a startup failure and verify a retry is offered.
- Out of scope: bypass browser safety policy.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: disposable Compose installation and restorable app service.
- Required browser context: signed-in browser with a previously loaded application shell.

## Allowed Mutations

- Allowed: briefly stop and restart only the disposable app service.
- Not allowed: bypass Browser Use URL policy or use alternate browser control paths.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Stopped the disposable app service and attempted to reload the existing in-app browser page, then tried a safer database-only outage while the web server remained reachable. | Failed startup shows a retry instead of a frozen splash. | Browser Use rejected the server-down navigation under its URL safety policy. The database-only reload remained pending until the execution deadline rather than reaching an observable retry UI. Both services were restored; a valid sign-in restored all 12 tracks. | BLOCKED | [assets/SGN_07-browser-policy-block.txt](../assets/SGN_07-browser-policy-block.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-browser-policy-block.txt](../assets/SGN_07-browser-policy-block.txt) | Exact blocked attempt and verified service restoration. |

## Screenshot Evidence

No screenshot is available because Browser Use blocked the failure-state navigation before capture.

## Timings

| Step | Timing |
|---|---:|
| App service stop | 1.6 s |
| Service restart to server startup log | about 35 s |

## Handoff Notes

- Completed: safe failure injection attempted; disposable service restored and healthy.
- Remaining unfinished coverage: SGN_08 onward.
- Blocked or not applicable: SGN_07 is terminal `BLOCKED` by the in-app browser URL safety policy.
- State left for the next packet: Compose installation healthy; a fresh supported browser tab is signed in with all 12 tracks.
