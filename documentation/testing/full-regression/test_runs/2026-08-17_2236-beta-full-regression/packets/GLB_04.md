# Packet: GLB_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_04
- In scope: Minimum/maximum zoom controls, panning at boundaries, and escape from each edge.
- Out of scope: Projection preference.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_03.
- Required app/data state: Globe explicitly enabled and map interactive.
- Required browser context: Signed-in desktop map.

## Allowed Mutations

- Allowed: Reversible zoom and pan camera changes.
- Not allowed: Reload/reset to escape a boundary.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_04 | Reached both zoom limits, panned, and escaped with the opposite zoom control. | Limits do not trap map at edges. | At min only Zoom out disabled; at max only Zoom in disabled. Pan worked, and one opposite zoom immediately restored both directions with canvas intact. | PASS | [assets/GLB_04-limits.txt](../assets/GLB_04-limits.txt); [assets/GLB_04-min-drag.jpg](../assets/GLB_04-min-drag.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_04-limits.txt](../assets/GLB_04-limits.txt) | Exact aria-disabled, pan, scale, and boundary-escape results. |
| [assets/GLB_04-min-drag.jpg](../assets/GLB_04-min-drag.jpg) | Durable rotated/interactable globe at the minimum edge. |

## Screenshot Evidence

- The saved image shows the globe rotated at minimum zoom with controls and data intact.

## Timings

| Step | Timing |
|---|---:|
| Boundary drag/escape | Under 1 s each |

## Handoff Notes

- Completed: Minimum and maximum zoom boundary behavior.
- Remaining unfinished coverage: None for GLB_04.
- Blocked or not applicable: None.
- State left for the next packet: Map one level below maximum over ocean; all navigation controls enabled.
