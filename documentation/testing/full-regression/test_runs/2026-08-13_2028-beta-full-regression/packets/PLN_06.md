# Packet: PLN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: PLN_06.
- In scope: elevation rendering and chart-to-map hover.
- Out of scope: saved plans, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_05.
- Required app/data state: restored four-leg route.
- Required browser context: Planner Drawing with elevation chart.

## Allowed Mutations

- Allowed: hover chart and zoom map out to expose the corresponding point.
- Not allowed: change route geometry.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_06 | Inspected the elevation chart, hovered at 3.62 km, and observed the map marker. | Chart renders and hover highlights the matching map point. | Chart contained 426 points; tooltip showed distance/elevation/grade and a 14 px marker appeared on the routed map coordinate. | PASS | [hover](../assets/PLN_06-elevation-hover.txt), [chart and marker](../assets/PLN_06-chart-hover.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_06-elevation-hover.txt](../assets/PLN_06-elevation-hover.txt) | Exact chart bounds, tooltip, and marker bounds. |
| [assets/PLN_06-chart-hover.webp](../assets/PLN_06-chart-hover.webp) | Visible chart tooltip and matching map marker. |

## Screenshot Evidence

The compact WebP shows both synchronized hover surfaces.

## Timings

| Step | Timing |
|---|---:|
| Tooltip and marker | < 100 ms |

## Handoff Notes

- Completed: PLN_06 is terminal `PASS`.
- Remaining unfinished coverage: PLN_07 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: four-leg Road Bike route restored; map zoomed out four levels; Planner open.
