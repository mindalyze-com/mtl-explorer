# Packet: SGN_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_02
- In scope: Sign in with README-documented credentials and verify the map is reached.
- Out of scope: Wrong credentials and sign-out; covered by SGN_03 and SGN_05.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: Eleven visible tracks after format imports.
- Required browser context: Fresh signed-out browser context.

## Allowed Mutations

- Allowed: Sign in as README user `mtl`.
- Not allowed: Change app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_02 | Entered valid README credentials and clicked **Sign In**. | User reaches the map. | Browser returned to `/mtl/`; map shell showed `11 Tracks`, Stats, Filter, Map, Animate, Segments, GPS, Planner, and Admin controls. | PASS | [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt), [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt) | Valid sign-in assertions and map text. |
| [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) | Map screenshot after valid sign-in. |

## Screenshot Evidence

**Map screenshot after valid sign-in.**

![Map screenshot after valid sign-in.](../assets/SGN_02-valid-login-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Valid sign-in to map loaded | ~6 seconds |

## Handoff Notes

- Completed: SGN_02 terminal as `PASS`.
- Remaining unfinished coverage: Continue with SGN_03.
- Blocked or not applicable: None.
- State left for the next packet: App state unchanged.
