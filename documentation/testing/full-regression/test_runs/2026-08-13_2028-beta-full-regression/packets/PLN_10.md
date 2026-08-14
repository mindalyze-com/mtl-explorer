# Packet: PLN_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_10.
- In scope: preservation of an existing plan while a new route request fails.
- Out of scope: mobile touch dragging, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_09.
- Required app/data state: loaded four-leg plan plus failing Reykjavík waypoint in history.
- Required browser context: Planner showing downloading then unavailable.

## Allowed Mutations

- Allowed: inspect old route through both failure states and undo failed edit.
- Not allowed: clear the old route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_10 | Observed the loaded route during segment download and route-unavailable retry, then undid the failed point. | Existing plan stays displayed when new routing fails. | Exact old stats, four legs, and 426-point profile remained through both states and after undo. | PASS | [preservation](../assets/PLN_10-existing-route.txt), [trouble state](../assets/PLN_09-segment-downloading.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_10-existing-route.txt](../assets/PLN_10-existing-route.txt) | Exact route state before/during/after failure and recovery. |
| [assets/PLN_09-segment-downloading.webp](../assets/PLN_09-segment-downloading.webp) | Existing plan visible under the routing-data notice. |

## Screenshot Evidence

The compact WebP directly shows the old route statistics and chart preserved beneath the downloading message.

## Timings

| Step | Timing |
|---|---:|
| Existing-route persistence | Continuous through 8 s retry |
| Undo recovery | < 1 s |

## Handoff Notes

- Completed: PLN_10 is terminal `PASS`.
- Remaining unfinished coverage: PLN_11 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Planner Drawing with clean 7.69 km route; map centered on Reykjavík; desktop viewport.
