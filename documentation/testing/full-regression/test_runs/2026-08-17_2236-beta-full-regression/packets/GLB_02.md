# Packet: GLB_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_02
- In scope: Automatic return to flat projection while zooming in.
- Out of scope: Manual globe preference and zoom boundaries.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_01.
- Required app/data state: Globe projection active.
- Required browser context: Signed-in desktop map.

## Allowed Mutations

- Allowed: Map Zoom in only.
- Not allowed: Manually toggle projection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_02 | Zoomed in five levels from globe. | Map returns to flat view. | Circular sphere expanded and changed to a full rectangular regional map with data/controls intact. | PASS | [assets/GLB_02-flat.txt](../assets/GLB_02-flat.txt); [assets/GLB_02-flat-return.jpg](../assets/GLB_02-flat-return.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_02-flat.txt](../assets/GLB_02-flat.txt) | Exact zoom-in and projection transition result. |
| [assets/GLB_02-flat-return.jpg](../assets/GLB_02-flat-return.jpg) | Durable flat regional map after globe return. |

## Screenshot Evidence

- The saved desktop image shows the restored rectangular regional projection and intact track data.

## Timings

| Step | Timing |
|---|---:|
| Each zoom transition | Under 300 ms |

## Handoff Notes

- Completed: Zoom-in flat return.
- Remaining unfinished coverage: None for GLB_02.
- Blocked or not applicable: None.
- State left for the next packet: Flat regional map active; automatic globe preference still enabled.
