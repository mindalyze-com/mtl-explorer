# Packet: GLB_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_01
- In scope: Zoom out far enough that globe mode engages automatically.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_04.
- Required app/data state: Usable map with globe mode initially inactive.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Close Search and use the map's Zoom out control.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_01 | Zoomed out from 100 m through the globe threshold and read the control state before and after entry. | Globe engages automatically at a far zoom. | Globe was inactive at 500 km, then automatically became pressed/active at 1000 km without manual globe activation. | PASS | [assets/GLB_01-auto-enter.txt](../assets/GLB_01-auto-enter.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_01-auto-enter.txt](../assets/GLB_01-auto-enter.txt) | Zoom sequence and direct globe-control state transition. |

## Screenshot Evidence

Live desktop inspection confirmed far-zoom globe entry. Direct `aria-pressed` and active-class evidence is durable; ACC_04 prevents a saved canvas screenshot.

## Timings

| Step | Timing |
|---|---:|
| Zoom sequence and settlement | About 6.5 s |

## Handoff Notes

- Completed: Automatic globe entry.
- Remaining unfinished coverage: None for GLB_01.
- Blocked or not applicable: Durable screenshot remains blocked by ACC_04.
- State left for the next packet: Globe active at 1000 km scale.
