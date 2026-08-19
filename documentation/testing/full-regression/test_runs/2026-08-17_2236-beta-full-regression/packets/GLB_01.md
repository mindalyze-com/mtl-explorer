# Packet: GLB_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_01
- In scope: Automatic globe engagement at low zoom.
- Out of scope: Zoom-in return, manual disable, and edge limits.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_04.
- Required app/data state: Healthy flat main map.
- Required browser context: Signed-in desktop map.

## Allowed Mutations

- Allowed: Map Zoom out only.
- Not allowed: Manually toggle globe during the automatic-engagement test.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_01 | Zoomed out from Bern through regional and global levels. | Globe view engages automatically. | Map changed without manual toggle to a circular earth with curved geography and surrounding whitespace; controls/data remained present. | PASS | [assets/GLB_01-auto.txt](../assets/GLB_01-auto.txt); [assets/GLB_01-auto-globe.jpg](../assets/GLB_01-auto-globe.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_01-auto.txt](../assets/GLB_01-auto.txt) | Exact transition sequence and visual criteria. |
| [assets/GLB_01-auto-globe.jpg](../assets/GLB_01-auto-globe.jpg) | Durable automatically engaged globe screenshot. |

## Screenshot Evidence

- The saved desktop image shows the circular earth after Zoom out alone.

## Timings

| Step | Timing |
|---|---:|
| Each zoom transition | Under 300 ms |

## Handoff Notes

- Completed: Automatic globe engagement.
- Remaining unfinished coverage: None for GLB_01.
- Blocked or not applicable: None.
- State left for the next packet: Globe projection active at global zoom.
