# Packet: SGN_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_02
- In scope: valid README credential sign-in and first map reachability.
- Out of scope: wrong-password and sign-out behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: app running; README credentials available.
- Required browser context: fresh unsigned desktop browser context.

## Allowed Mutations

- Allowed: submit login form with README credentials.
- Not allowed: modify server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_02 | Signed in with `mtl` / `change-me` from the login screen. | Valid credentials reach the map. | Login succeeded, URL returned to `/mtl/`, and the map shell showed 10 tracks plus primary navigation. | PASS | `assets/SGN_02-valid-login-map.webp`, `assets/SGN_02-valid-login-map.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_02-valid-login-map.webp | Map after valid sign-in. |
| assets/SGN_02-valid-login-map.txt | URL, timing, and visible text summary. |

## Timings

| Step | Timing |
|---|---:|
| Valid login to ready map | 5.2s |

## Handoff Notes

- Completed: SGN_02 terminal PASS.
- Remaining unfinished coverage: continue with SGN_03.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
