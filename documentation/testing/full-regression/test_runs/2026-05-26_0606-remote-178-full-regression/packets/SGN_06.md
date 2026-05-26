# Packet: SGN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_06
- In scope: startup splash/loading state and transition to ready map.
- Out of scope: startup failure/retry behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: app running.
- Required browser context: fresh desktop browser context.

## Allowed Mutations

- Allowed: sign in and observe startup.
- Not allowed: interrupt server availability.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Captured the startup state immediately after valid sign-in, then waited for the ready map. | Splash/logo/message displays during startup and disappears once map/tracks are loaded. | `LOADING YOUR TRAILS` appeared during startup, then disappeared after 2.6s and the ready map showed 10 tracks. | PASS | `assets/SGN_06-splash-loading.webp`, `assets/SGN_06-ready-map.webp`, `assets/SGN_06-splash-summary.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_06-splash-loading.webp | Startup loading state. |
| assets/SGN_06-ready-map.webp | Map after loading state disappeared. |
| assets/SGN_06-splash-summary.txt | Text and timing summary. |

## Timings

| Step | Timing |
|---|---:|
| Splash to ready map | 2.6s |

## Handoff Notes

- Completed: SGN_06 terminal PASS.
- Remaining unfinished coverage: continue with SGN_07.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
