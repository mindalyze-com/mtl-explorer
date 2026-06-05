# Packet: NET_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_02
- In scope: Recoverable UI state under flaky/API-failed connection.
- Out of scope: Installed-PWA offline reload; covered by NET_01 applicability.

## Prerequisites

- Required previous coverage IDs or run packets: NET_01.
- Required app/data state: Isolated authenticated browser context.
- Required browser context: Desktop Chromium context with API request interception.

## Allowed Mutations

- Allowed: Clear isolated browser local caches and abort API requests in that context.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Logged in, cleared local browser caches except JWT, aborted `/mtl/api/**`, and reloaded the app. | A flaky connection shows recoverable error states, not a blank screen. | The app showed `Unable to load tracks - no server connection and no cached data available.` with a visible `Retry` button. The shell/nav/map remained visible with `0 Tracks`; the screen was not blank. | PASS | [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt); [assets/NET_02-network-recovery.webp](../assets/NET_02-network-recovery.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt) | Aborted request sample, visible error text, Retry detection, and nonblank-shell check. |
| [assets/NET_02-network-recovery.webp](../assets/NET_02-network-recovery.webp) | Recoverable network-error UI. |

## Screenshot Evidence

**Recoverable network-error UI.**

![Recoverable network-error UI.](../assets/NET_02-network-recovery.webp)

## Timings

| Step | Timing |
|---|---:|
| Network failure recovery check | ~2 min |

## Handoff Notes

- Completed: NET_02 terminal as `PASS`.
- Remaining unfinished coverage: Continue with NET_03.
- Blocked or not applicable: None.
- State left for the next packet: Isolated failure context closed; server state unchanged.
