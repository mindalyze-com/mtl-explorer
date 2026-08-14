# Packet: PLN_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_11.
- In scope: touch dragging at mobile width.
- Out of scope: measuring tools.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_10.
- Required app/data state: clean four-leg route centered on Zürich.
- Required browser context: 390×844 responsive viewport.

## Allowed Mutations

- Allowed: perform the closest mobile pointer drag, inspect capabilities, undo edits, restore viewport.
- Not allowed: misreport mouse events as touch events.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_11 | Opened Planner at 390×844, inserted and dragged a waypoint, then inspected event capabilities. | Touch dragging places and moves waypoints. | Mobile layout and coordinate pointer drag worked and rerouted. Real touch could not be generated because the browser exposes no touch capability. | BLOCKED | [mobile drag](../assets/PLN_11-mobile-drag.txt) |

## Issues

No product issue assigned because the missing assertion is specific to unavailable touch-event generation.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_11-mobile-drag.txt](../assets/PLN_11-mobile-drag.txt) | Mobile pointer results, capability boundary, cleanup, and restored state. |

## Screenshot Evidence

The responsive route layout was inspected directly; touch attribution remains blocked.

## Timings

| Step | Timing |
|---|---:|
| Mobile pointer reroute | < 2 s |
| Undo cleanup | < 3 s |

## Handoff Notes

- Completed: PLN_11 is terminal `BLOCKED`; mobile mouse-pointer path passed, true touch was unavailable.
- Remaining unfinished coverage: MCT_01 onward.
- Blocked or not applicable: touch-event assertion only.
- State left for the next packet: desktop restored; Planner open with original 7.69 km route.
