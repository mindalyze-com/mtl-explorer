# Packet: SGN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_07
- In scope: Verify startup failure offers retry instead of freezing.
- Out of scope: Server shutdown or destructive infrastructure changes.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: App running.
- Required browser context: Fresh signed-out context with client-side API route aborts after login.

## Allowed Mutations

- Allowed: Simulate startup API failure in Playwright by aborting non-login `/mtl/api/*` requests.
- Not allowed: Stop the real server or mutate app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Allowed `/auth/login` but aborted other API calls during startup. | Startup failure offers retry instead of frozen splash. | App showed `Unable to load tracks — no server connection and no cached data available.` plus a visible `Retry` control; map shell remained responsive with `0 Tracks`. | PASS | [assets/SGN_07-startup-failure-retry.txt](../assets/SGN_07-startup-failure-retry.txt), [assets/SGN_07-startup-failure-retry.webp](../assets/SGN_07-startup-failure-retry.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-startup-failure-retry.txt](../assets/SGN_07-startup-failure-retry.txt) | Failure simulation method and observed retry message. |
| [assets/SGN_07-startup-failure-retry.webp](../assets/SGN_07-startup-failure-retry.webp) | Startup failure UI with Retry control. |

## Screenshot Evidence

**Startup failure UI with Retry control.**

![Startup failure UI with Retry control.](../assets/SGN_07-startup-failure-retry.webp)

## Timings

| Step | Timing |
|---|---:|
| Failure message/retry after login | ~1 second |

## Handoff Notes

- Completed: SGN_07 terminal as `PASS`.
- Remaining unfinished coverage: Continue with SGN_08.
- Blocked or not applicable: None.
- State left for the next packet: Failure was browser-local; server/app state unchanged.
