# Packet: GLB_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_04
- In scope: Verify globe/map zoom limits do not trap interaction at either edge.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_03.
- Required app/data state: Active globe at its far zoom.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Exercise only the map Zoom in/out controls through both limits.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_04 | Reached the 1000 km minimum and 1 m maximum, then left each boundary using the opposite zoom control. | Zoom limits do not trap map interaction. | Edge controls disabled correctly; the opposite control immediately left each edge (500 km and 2 m), with normal controls intact. | PASS | [assets/GLB_04-zoom-limits.txt](../assets/GLB_04-zoom-limits.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_04-zoom-limits.txt](../assets/GLB_04-zoom-limits.txt) | Direct scale and disabled/enabled control states at both edges. |

## Screenshot Evidence

Live desktop inspection confirmed both boundaries and recovery. Direct control-state evidence is durable; ACC_04 prevents a saved canvas screenshot.

## Timings

| Step | Timing |
|---|---:|
| Both boundary checks | About 16 s |

## Handoff Notes

- Completed: Minimum/maximum zoom-boundary interaction.
- Remaining unfinished coverage: None for GLB_04.
- Blocked or not applicable: Durable screenshot remains blocked by ACC_04.
- State left for the next packet: Flat map at 2 m scale; both zoom controls enabled.
