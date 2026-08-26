# Packet: SGN_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_02
- In scope: Sign in with valid documented credentials and reach the map.
- Out of scope: Invalid credentials, covered by SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: Login page available; server healthy.
- Required browser context: Signed-out login tab.

## Allowed Mutations

- Allowed: Enter README credentials and activate visible Sign In.
- Not allowed: Store credential values in run artifacts.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| SGN_02 | Submit the documented valid credentials and wait for map/data load. | Login succeeds and map becomes usable. | Redirected to `/mtl/`; two map regions, normal navigation, and nine active tracks appeared; login controls disappeared. | PASS | [assets/SGN_02-valid-login.txt](../assets/SGN_02-valid-login.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-login.txt](../assets/SGN_02-valid-login.txt) | Valid-login action source and settled map state without credential values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible map/login state is recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Valid login to settled map | <2 s |

## Handoff Notes

- Completed: Valid credential login and settled map verification.
- Remaining unfinished coverage: None for SGN_02.
- Blocked or not applicable: None.
- State left for the next packet: Browser signed in at map; invalid-login check needs a signed-out tab/state.
