# Packet: SGN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_01
- In scope: Signed-out root navigation and login redirect.
- Out of scope: Credential validation.

## Prerequisites

- Required previous coverage IDs or run packets: MED_06.
- Required app/data state: Existing populated run; browser signed out through the UI.
- Required browser context: Signed-out main tab.

## Allowed Mutations

- Allowed: Sign out and navigate to the app root.
- Not allowed: Clear application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Signed out, then opened the app root directly. | Redirect to login. | Final URL was `/mtl/login` with Username, Password, and Sign In controls. | PASS | [assets/SGN_01-redirect.txt](../assets/SGN_01-redirect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-redirect.txt](../assets/SGN_01-redirect.txt) | Signed-out URL and visible login controls. |

## Screenshot Evidence

Blocked by ACC_04; direct URL and DOM evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Sign out and redirect | Under 2 s |

## Handoff Notes

- Completed: Signed-out access redirects to login.
- Remaining unfinished coverage: None for SGN_01.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Login page open and signed out.
