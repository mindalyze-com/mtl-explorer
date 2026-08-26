# Packet: SGN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_04
- In scope: If demo mode is active, verify the login demo-credentials banner.
- Out of scope: Enabling demo mode on this standard quick-install run.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_03.
- Required app/data state: Signed-out login page; effective public demo status available.
- Required browser context: Login page in the in-app browser.

## Allowed Mutations

- Allowed: Read the public demo-status endpoint and inspect the login page.
- Not allowed: Change the Compose profile or enable demo mode.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Inspect the public demo status and signed-out login page. | When demo mode is active, show the demo credentials banner. | The public endpoint returned `demoMode:false` with empty credential fields; the standard login page had no demo banner. The conditional precondition is false. | NOT APPLICABLE | [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt) | Public demo-mode value and visible signed-out login controls. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the public status and accessible login state are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Demo status and login inspection | <1 min |

## Handoff Notes

- Completed: Conditional applicability check.
- Remaining unfinished coverage: None for SGN_04.
- Blocked or not applicable: NOT APPLICABLE because the fresh quick-install instance reports demo mode disabled.
- State left for the next packet: Browser remains signed out at `/mtl/login`.
