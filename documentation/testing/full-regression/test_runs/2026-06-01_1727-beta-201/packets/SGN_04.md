# Packet: SGN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_04
- In scope: Decide whether the conditional demo-mode login banner applies.
- Out of scope: Enabling demo mode or changing deployment configuration.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: Quick-install app login available.
- Required browser context: Fresh signed-out browser context.

## Allowed Mutations

- Allowed: Review login screen evidence.
- Not allowed: Modify configuration to enable demo mode.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Reviewed the signed-out login screen for demo-mode credentials banner. | If demo mode is active, demo credentials banner is shown. | Demo mode was not active in this quick-install run; login screen had no demo credentials banner. | NOT APPLICABLE | [assets/SGN_04-demo-mode-scope.txt](../assets/SGN_04-demo-mode-scope.txt), [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt), [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-mode-scope.txt](../assets/SGN_04-demo-mode-scope.txt) | Demo-mode applicability decision. |
| [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt) | Login-screen text evidence. |
| [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp) | Login-screen screenshot evidence. |

## Screenshot Evidence

**Login-screen screenshot evidence.**

![Login-screen screenshot evidence.](../assets/SGN_01-signed-out-login.webp)

## Timings

| Step | Timing |
|---|---:|
| Demo-mode applicability review | <1 minute |

## Handoff Notes

- Completed: SGN_04 terminal as `NOT APPLICABLE`.
- Remaining unfinished coverage: Continue with SGN_05.
- Blocked or not applicable: Demo mode was not active.
- State left for the next packet: App state unchanged.
