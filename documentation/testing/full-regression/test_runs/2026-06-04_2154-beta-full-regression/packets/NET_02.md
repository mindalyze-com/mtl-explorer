# Packet: NET_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_02
- In scope: Flaky connection and recoverable failed track-load state.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Blocked /mtl/api/tracks/get-simplified requests in Playwright, loaded MTL Explorer, and observed the recovery UI. | A flaky track request shows an actionable recoverable error state, including Retry, and the page remains nonblank. | The blocked requests produced the visible message 'Unable to load tracks — no server connection and no cached data available.' with a Retry button while map/app chrome remained visible. Assertions requestsBlocked, retryVisible, actionableError, and notBlank were all true. | PASS | [NET_02-flaky-track-load.webp](../assets/NET_02-flaky-track-load.webp); [NET_02-flaky-track-load.txt](../assets/NET_02-flaky-track-load.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [NET_02-flaky-track-load.webp](../assets/NET_02-flaky-track-load.webp) | Screenshot evidence |
| [NET_02-flaky-track-load.txt](../assets/NET_02-flaky-track-load.txt) | Text/log evidence |

## Screenshot Evidence

![NET_02-flaky-track-load.webp](../assets/NET_02-flaky-track-load.webp)

## Timings

| Step | Timing |
|---|---:|
| Route interception and load | 12 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
