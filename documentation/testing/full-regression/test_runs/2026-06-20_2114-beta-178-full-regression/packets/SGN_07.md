# Packet: SGN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_07
- In scope: Startup failure shows retry instead of a frozen splash.
- Out of scope: normal splash behavior; covered by SGN_06.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: authenticated session.
- Required browser context: authenticated desktop browser context.

## Allowed Mutations

- Allowed: abort app API requests in the browser test context.
- Not allowed: stop or reconfigure the server.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Opened the app while aborting `/mtl/api/**` requests in the browser context. | If startup fails, a retry is offered instead of a frozen splash. | PASS: the app showed `Unable to load tracks - no server connection and no cached data available.` with a visible `Retry` button; the splash loading text was gone. | PASS | [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt); [assets/SGN_07-startup-failure.webp](../assets/SGN_07-startup-failure.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) | Aborted-request count, retry text, and non-frozen UI evidence. |
| [assets/SGN_07-startup-failure.webp](../assets/SGN_07-startup-failure.webp) | Startup failure screen with Retry button. |

## Screenshot Evidence

![Startup failure retry](../assets/SGN_07-startup-failure.webp)

## Timings

| Step | Timing |
|---|---:|
| Startup failure simulation | ~9 seconds |

## Handoff Notes

- Completed: SGN_07 is terminal.
- Remaining unfinished coverage: SGN_08 onward.
- Blocked or not applicable: none.
- State left for the next packet: no server mutations; only browser-context route aborts were used.
