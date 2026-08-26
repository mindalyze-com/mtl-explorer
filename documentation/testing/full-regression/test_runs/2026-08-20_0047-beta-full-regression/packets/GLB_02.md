# Packet: GLB_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_02
- In scope: Zoom in and confirm the view returns to flat-map behavior.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_01.
- Required app/data state: Globe pressed/active at 1000 km.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Use the map's Zoom in control.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_02 | Zoomed in five levels from active globe mode and inspected the settled controls. | View returns to the flat map. | At 50 km the globe-zone control was gone, while the normal map controls remained usable, confirming automatic exit from globe mode. | PASS | [assets/GLB_02-auto-exit.txt](../assets/GLB_02-auto-exit.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_02-auto-exit.txt](../assets/GLB_02-auto-exit.txt) | Globe exit and resulting map/control state. |

## Screenshot Evidence

Live desktop inspection confirmed the flat-map return. ACC_04 prevents a saved canvas screenshot.

## Timings

| Step | Timing |
|---|---:|
| Zoom-in sequence and settlement | About 3.4 s |

## Handoff Notes

- Completed: Automatic globe exit on zoom-in.
- Remaining unfinished coverage: None for GLB_02.
- Blocked or not applicable: Durable screenshot remains blocked by ACC_04.
- State left for the next packet: Flat map at 50 km; globe control outside visible zone.
