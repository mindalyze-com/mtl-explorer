# Packet: SGN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_04
- In scope: Conditional demo-mode credentials banner on the login screen.
- Out of scope: Normal login behavior when demo mode is inactive.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: app running.
- Required browser context: signed-out desktop browser context.

## Allowed Mutations

- Allowed: query public demo-status endpoint and inspect login screen.
- Not allowed: enable or disable demo mode.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Queried `/mtl/api/auth/demo-status` and inspected the signed-out login screen. | If demo mode is active, the login screen shows the demo credentials banner. | NOT APPLICABLE: demo status returned `{"demoMode":false,"username":"","password":""}` and the login screen showed no demo banner, so the conditional demo-mode requirement does not apply to this target. | NOT APPLICABLE | [assets/SGN_04-demo-mode.txt](../assets/SGN_04-demo-mode.txt); [assets/SGN_04-demo-mode-login.webp](../assets/SGN_04-demo-mode-login.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-mode.txt](../assets/SGN_04-demo-mode.txt) | Demo-status response and applicability decision. |
| [assets/SGN_04-demo-mode-login.webp](../assets/SGN_04-demo-mode-login.webp) | Login screen screenshot without demo banner. |

## Screenshot Evidence

![Login screen with demo mode inactive](../assets/SGN_04-demo-mode-login.webp)

## Timings

| Step | Timing |
|---|---:|
| Demo-status and login inspection | ~3 seconds |

## Handoff Notes

- Completed: SGN_04 is terminal as NOT APPLICABLE.
- Remaining unfinished coverage: SGN_05 onward.
- Blocked or not applicable: SGN_04 not applicable because demo mode is inactive on this target.
- State left for the next packet: signed-out inspection context was closed.
