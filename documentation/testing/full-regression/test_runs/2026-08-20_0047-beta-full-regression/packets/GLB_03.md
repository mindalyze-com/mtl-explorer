# Packet: GLB_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GLB_03
- In scope: Verify a manual globe disable is respected until manual re-enable.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_02.
- Required app/data state: Flat map with globe control available after zooming out.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Zoom within the globe zone and toggle globe mode twice.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GLB_03 | Disabled an automatically active globe, zoomed in/out within the globe zone, then manually re-enabled it. | Manual disable is respected; globe does not auto-re-enable until requested. | Pressed state changed true→false, stayed false through the zoom cycle, then returned to true only after the second manual toggle. | PASS | [assets/GLB_03-manual-override.txt](../assets/GLB_03-manual-override.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GLB_03-manual-override.txt](../assets/GLB_03-manual-override.txt) | Direct pressed-state sequence across disable, zoom, and re-enable. |

## Screenshot Evidence

Live desktop inspection confirmed the projection toggles. Direct pressed/active-class evidence is durable; ACC_04 prevents a saved canvas screenshot.

## Timings

| Step | Timing |
|---|---:|
| Full override sequence | About 5.8 s |

## Handoff Notes

- Completed: Manual disable persistence and explicit re-enable.
- Remaining unfinished coverage: None for GLB_03.
- Blocked or not applicable: Durable screenshot remains blocked by ACC_04.
- State left for the next packet: Globe manually enabled at 1000 km.
