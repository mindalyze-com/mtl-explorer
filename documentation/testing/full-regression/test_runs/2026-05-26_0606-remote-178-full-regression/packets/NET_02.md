# Packet: NET_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_02
- In scope: Recoverable error state under API/network failure.
- Out of scope: Physically stopping the shared server.

## Prerequisites

- Required previous coverage IDs or run packets: NET_01
- Required app/data state: App running on target.
- Required browser context: Isolated browser context for network interception.

## Allowed Mutations

- Allowed: Use isolated browser context, clear that context's cache/IndexedDB, and abort API requests.
- Not allowed: Stop the target server or mutate shared app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Logged in in an isolated browser context, cleared cached track data, aborted `/mtl/api/**`, and reloaded. | A flaky connection shows recoverable error states, not a blank screen. | The app showed `Unable to load tracks - no server connection and no cached data available.` with a visible Retry button; splash was not frozen and the map shell/nav remained visible. | PASS | `assets/NET_02-network-recovery.txt`, `assets/NET_02-network-recovery.spec.ts` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/NET_02-network-recovery.txt | Network failure output and visible recovery state. |
| assets/NET_02-network-recovery.spec.ts | Isolated Playwright check used to abort API requests. |

## Timings

| Step | Timing |
|---|---:|
| Isolated network failure check | <5 min |

## Handoff Notes

- Completed: NET_02 passed.
- Remaining unfinished coverage: NET_03 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Main browser remains signed in; isolated test context closed.
