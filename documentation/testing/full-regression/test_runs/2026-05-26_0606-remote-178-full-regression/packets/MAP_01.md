# Packet: MAP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_01
- In scope: first-open map shell, base map, controls, and track overlay presence.
- Out of scope: detailed overlay switching, covered later by HMO_02.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: signed-in app with imported tracks.
- Required browser context: fresh desktop browser context.

## Allowed Mutations

- Allowed: open app after sign-in.
- Not allowed: change map settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Opened the app after valid sign-in and observed the first map view. | Base map and overlays load on first open. | The first ready map rendered map controls, scale, primary navigation, and 10 visible tracks without a blank-map state. | PASS | `assets/SGN_02-valid-login-map.webp`, `assets/SGN_02-valid-login-map.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_02-valid-login-map.webp | First ready map after sign-in. |
| assets/SGN_02-valid-login-map.txt | First-open map text summary. |

## Timings

| Step | Timing |
|---|---:|
| First map load | 5.2s |

## Handoff Notes

- Completed: MAP_01 terminal PASS.
- Remaining unfinished coverage: continue with MAP_02.
- Blocked or not applicable: detailed overlay switching deferred to HMO_02.
- State left for the next packet: no data changed.
