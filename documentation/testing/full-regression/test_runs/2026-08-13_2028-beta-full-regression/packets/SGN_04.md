# Packet: SGN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SGN_04.
- In scope: evaluate the conditional demo-credentials banner requirement.
- Out of scope: enable demo mode solely to manufacture the condition.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_03.
- Required app/data state: unchanged quick-install authentication configuration.
- Required browser context: signed-out login screen.

## Allowed Mutations

- Allowed: inspect the public demo-status response and login screen.
- Not allowed: change server demo-mode configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Inspected the signed-out login screen and public demo-status response. | If demo mode is active, the login screen shows the demo credentials banner. | Demo mode is false and both advertised demo credential fields are empty. The conditional banner requirement does not apply. | NOT APPLICABLE | [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt); [assets/SGN_01-signed-out-redirect.webp](../assets/SGN_01-signed-out-redirect.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt) | Confirms the conditional prerequisite is false. |
| [assets/SGN_01-signed-out-redirect.webp](../assets/SGN_01-signed-out-redirect.webp) | Signed-out login screen without a demo banner. |

## Screenshot Evidence

![Login screen with demo mode inactive](../assets/SGN_01-signed-out-redirect.webp)

## Timings

| Step | Timing |
|---|---:|
| Applicability check | < 1 min |

## Handoff Notes

- Completed: demo-mode condition evaluated.
- Remaining unfinished coverage: SGN_05 onward.
- Blocked or not applicable: SGN_04 is terminal `NOT APPLICABLE` because demo mode is off.
- State left for the next packet: signed out on login.
