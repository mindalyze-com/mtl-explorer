# Packet: NET_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` section 19.
- Coverage ID or run packet: NET_02.
- In scope: Simulate a flaky connection and verify the app shows a recoverable state rather than a blank or frozen UI.
- Out of scope: Installed-PWA offline reload behavior; covered as not applicable in NET_01.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, SGN_02, SYN_07, NET_01.
- Required app/data state: Synced data set with 16 tracks.
- Required browser context: Isolated authenticated desktop context.

## Allowed Mutations

- Allowed: Browser-context request interception for `/mtl/api/**`; clicking visible Retry after restoring the route.
- Not allowed: Stop the server, alter remote networking, or mutate persistent app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Loaded MTL Explorer while aborting `/mtl/api/**` requests, captured the visible failure state, removed the request abort, and clicked Retry. | A flaky connection shows a recoverable message/control and does not leave the app blank or stuck on the splash; retry recovers when the connection returns. | With 17 intentionally aborted API calls, the app showed `Unable to load tracks - no server connection and no cached data available.` and a visible Retry button; it was not blank and the splash was gone. After restoring requests and clicking Retry, the app returned to the map with `16 Tracks`, two canvases, no Retry button, and no splash. | PASS | [assets/NET_02-flaky-recovery.txt](../assets/NET_02-flaky-recovery.txt); [assets/NET_02-flaky-error.webp](../assets/NET_02-flaky-error.webp); [assets/NET_02-retry-recovered.webp](../assets/NET_02-retry-recovered.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_02-flaky-recovery.txt](../assets/NET_02-flaky-recovery.txt) | Request abort count, failure-state text, retry recovery state, and expected induced network errors. |
| [assets/NET_02-flaky-error.webp](../assets/NET_02-flaky-error.webp) | Recoverable network error state with Retry. |
| [assets/NET_02-retry-recovered.webp](../assets/NET_02-retry-recovered.webp) | Recovered map state after restoring network and clicking Retry. |

## Screenshot Evidence

![Recoverable network error state](../assets/NET_02-flaky-error.webp)

![Recovered after retry](../assets/NET_02-retry-recovered.webp)

## Timings

| Step | Timing |
|---|---:|
| Flaky connection simulation and retry recovery | 2026-06-21 06:12 CEST |

## Handoff Notes

- Completed: NET_02 passed with direct simulated network failure and recovery evidence.
- Remaining unfinished coverage: NET_03, NET_04, ERR_01, ERR_02, RUN_CLEANUP.
- Blocked or not applicable: none.
- State left for the next packet: No server mutations; request interception existed only in the closed browser context.
