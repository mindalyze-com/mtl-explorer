# Packet: SGN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SGN_02.
- In scope: sign in with the documented valid credentials.
- Out of scope: invalid-credential handling.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: healthy application with 12 visible tracks.
- Required browser context: signed-out login screen.

## Allowed Mutations

- Allowed: submit the documented local login credentials.
- Not allowed: change authentication configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_02 | Entered `mtl` / `change-me` and selected Sign In. | Authentication succeeds and the map is reached. | The URL changed to `/mtl/` in 1.67 s; the map and `12 Tracks` control loaded. | PASS | [assets/SGN_02-valid-sign-in.webp](../assets/SGN_02-valid-sign-in.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-sign-in.webp](../assets/SGN_02-valid-sign-in.webp) | Loaded map after valid sign-in. |

## Screenshot Evidence

![Valid sign-in map](../assets/SGN_02-valid-sign-in.webp)

## Timings

| Step | Timing |
|---|---:|
| Sign In to loaded map | 1.67 s |

## Handoff Notes

- Completed: valid sign-in flow.
- Remaining unfinished coverage: SGN_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: signed in on the 12-track map.
