# Packet: PLN_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_09
- In scope: Clear Planner state when BRouter data/service is unavailable.
- Out of scope: Existing-route preservation, covered by PLN_10.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_08.
- Required app/data state: Disposable Compose install; temporary route active.
- Required browser context: Desktop Planner.

## Allowed Mutations

- Allowed: Test uncached remote regions and briefly stop/restart disposable BRouter.
- Not allowed: Leave BRouter stopped or alter original app/data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_09 | Try three uncached regions, then stop disposable BRouter and request a reroute. | UI shows segment downloading/unavailable instead of an unhandled error. | Natural regions routed normally. With BRouter stopped, status changed to unavailable and Planner showed a clear route-unavailable recovery message; no crash/unhandled UI appeared. | PASS | [assets/PLN_09-brouter-unavailable.txt](../assets/PLN_09-brouter-unavailable.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_09-brouter-unavailable.txt](../assets/PLN_09-brouter-unavailable.txt) | Natural routing attempts and controlled unavailable-engine UI evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible status/error copy is linked above.

## Timings

| Step | Timing |
|---|---:|
| Uncached-region attempts | 4 min |
| Controlled BRouter outage | 2 min |

## Handoff Notes

- Completed: BRouter unavailable UI and recovery-path messaging.
- Remaining unfinished coverage: None for PLN_09.
- Blocked or not applicable: Natural missing-segment state did not persist because requested segments became routable.
- State left for the next packet: BRouter restarted and ready; pre-outage route still displayed with the last route-unavailable message.
