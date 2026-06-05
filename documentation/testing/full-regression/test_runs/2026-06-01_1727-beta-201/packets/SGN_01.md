# Packet: SGN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_01
- In scope: Open the app while signed out and verify redirect to login.
- Out of scope: Valid and invalid sign-in attempts; covered by SGN_02-SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_02.
- Required app/data state: App running.
- Required browser context: Fresh browser context with no login cookies.

## Allowed Mutations

- Allowed: Open documented app URL.
- Not allowed: Sign in or change app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Opened `http://167.233.16.201:18080/mtl/` in a fresh signed-out context. | User is redirected to the login screen. | Browser landed on `/mtl/login`; sign-in screen was displayed. | PASS | [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt), [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt) | URL and signed-out login assertions. |
| [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp) | Signed-out login screen screenshot. |

## Screenshot Evidence

**Signed-out login screen screenshot.**

![Signed-out login screen screenshot.](../assets/SGN_01-signed-out-login.webp)

## Timings

| Step | Timing |
|---|---:|
| Signed-out open to login | ~1.5 seconds |

## Handoff Notes

- Completed: SGN_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with SGN_02.
- Blocked or not applicable: None.
- State left for the next packet: App state unchanged.
