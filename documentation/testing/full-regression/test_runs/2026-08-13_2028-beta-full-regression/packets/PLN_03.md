# Packet: PLN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_03.
- In scope: inserting a waypoint by dragging an existing route leg.
- Out of scope: general move/delete/undo/redo, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: visible computed Road Bike route.
- Required browser context: Zürich map with a leg exposed above the sheet.

## Allowed Mutations

- Allowed: drag one existing route leg.
- Not allowed: clear the route.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_03 | Dragged an exposed route leg to a new location. | A waypoint is inserted into that leg and the route recalculates. | Legs increased 4→5; distance, duration, geometry, and elevation chart recalculated. | PASS | [insertion](../assets/PLN_03-route-insertion.txt), [route](../assets/PLN_03-inserted.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_03-route-insertion.txt](../assets/PLN_03-route-insertion.txt) | Exact before/after route metrics. |
| [assets/PLN_03-inserted.webp](../assets/PLN_03-inserted.webp) | Five-leg route after insertion. |

## Screenshot Evidence

The compact WebP shows the recalculated five-leg route.

## Timings

| Step | Timing |
|---|---:|
| Route insertion/recompute | < 2 s |

## Handoff Notes

- Completed: PLN_03 is terminal `PASS`.
- Remaining unfinished coverage: PLN_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: five-leg Road Bike route, 8.05 km; an inserted waypoint is selected.
