# Packet: SGN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_02
- In scope: Valid credential sign-in.
- Out of scope: Invalid credentials and sign-out.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: Login page open; documented valid account available.
- Required browser context: Signed-out main tab.

## Allowed Mutations

- Allowed: Submit valid login.
- Not allowed: Store credentials in evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_02 | Entered the documented valid credentials and selected Sign In. | Reach the map. | Navigated to `/mtl/`; map region and 14-track control loaded; login control disappeared. | PASS | [assets/SGN_02-valid-login.txt](../assets/SGN_02-valid-login.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-login.txt](../assets/SGN_02-valid-login.txt) | Final route and signed-in map state. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and URL evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Sign-in to populated map | About 1.8 s |

## Handoff Notes

- Completed: Valid sign-in succeeds.
- Remaining unfinished coverage: None for SGN_02.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Signed in on the populated map.
