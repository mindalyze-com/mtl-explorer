# Packet: SGN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_04
- In scope: Conditional demo credentials banner.
- Out of scope: Enabling demo mode.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_03.
- Required app/data state: Login page open.
- Required browser context: Signed-out main tab.

## Allowed Mutations

- Allowed: Read login UI and public demo status.
- Not allowed: Change demo-mode configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Checked the login UI and public demo-status response. | If demo mode is active, show the demo credentials banner. | Runtime reports `demoMode:false`; no banner is present. The conditional requirement does not apply. | NOT APPLICABLE | [assets/SGN_04-demo-mode.txt](../assets/SGN_04-demo-mode.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-mode.txt](../assets/SGN_04-demo-mode.txt) | Public runtime flag and matching login UI. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and runtime evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| UI and public status check | Under 1 s |

## Handoff Notes

- Completed: Confirmed the conditional path is inactive.
- Remaining unfinished coverage: None for SGN_04.
- Blocked or not applicable: NOT APPLICABLE because demo mode is false.
- State left for the next packet: Signed out on login.
