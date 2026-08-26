# Packet: HMO_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_01
- In scope: Heatmap enable/disable, opacity controls, coexistence with enabled tracks, and rendered over-map/non-hiding behavior.
- Out of scope: Route overlays and filter-driven updates.

## Prerequisites

- Required previous coverage IDs or run packets: Map and track baseline; MED_12 complete.
- Required app/data state: Eight visible tracks; map settings at heatmap-off baseline.
- Required browser context: Authenticated Map > Your data.

## Allowed Mutations

- Allowed: Toggle Heatmap and adjust/restore its opacity.
- Not allowed: Change filters or other layer settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_01 | Enabled Heatmap, verified independent GPS-track enabled state, exercised opacity at 100/0/80%, then restored heatmap off/100%. | Heatmap visibly draws over the map without hiding tracks and respects opacity. | Enable/disable, 3/4->4/4, exact opacity values, eight-track count, and independent track enabled state all passed. Pixel-level draw/stacking/non-hiding proof is blocked because the map/heatmap is canvas-rendered and ACC_04 cannot capture it. | BLOCKED | [assets/HMO_01-heatmap-controls.txt](../assets/HMO_01-heatmap-controls.txt); [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

No product issue. The blocked child is rendered canvas verification under ACC_04.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_01-heatmap-controls.txt](../assets/HMO_01-heatmap-controls.txt) | Exact layer/toggle/opacity/track state and restored baseline. |
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Canvas screenshot constraint and unblock conditions. |

## Screenshot Evidence

None: the required rendered-canvas relationship is the blocked capability.

## Timings

| Step | Timing |
|---|---:|
| Heatmap toggle settlement | About 0.5 s |
| Opacity updates | Under 0.2 s each |

## Handoff Notes

- Completed: Toggle, layer counts, independent track state, 0/80/100 opacity, and restoration.
- Remaining unfinished coverage: None for HMO_01; terminal BLOCKED only for rendered-pixel proof.
- Blocked or not applicable: ACC_04 canvas capture/inspection.
- State left for the next packet: Map > Your data open; heatmap off at restored 100%; other three data layers on at 100%.
