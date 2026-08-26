# Packet: HMO_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_02
- In scope: Every worldwide/Swiss route overlay independently, each opacity slider, attribution, and rendered ordering relative to tracks.
- Out of scope: Heatmap/filter update.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_01 with heatmap restored off.
- Required app/data state: Eight-track map; route overlays all off.
- Required browser context: Authenticated Map > Route overlays.

## Allowed Mutations

- Allowed: Enable one route overlay at a time, adjust/restore opacity, and disable it.
- Not allowed: Leave overlays enabled or change track/filter state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_02 | Independently enabled all seven worldwide/Swiss overlays, checked 0/100 opacity and expected attribution, disabled each before the next, then verified 0/7 restored. | Every overlay toggles independently, opacity works, and rendered ordering above/below tracks is correct. | All seven control/opacity/attribution/independence paths pass and GPS tracks remain enabled. Pixel-level ordering relative to tracks is canvas-rendered and cannot be directly captured under ACC_04. | BLOCKED | [assets/HMO_02-route-overlays.txt](../assets/HMO_02-route-overlays.txt); [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

No product issue. The blocked child is rendered layer-order proof under ACC_04.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_02-route-overlays.txt](../assets/HMO_02-route-overlays.txt) | All seven exact overlay, opacity, attribution, independence, and baseline-restoration results. |
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Canvas screenshot constraint and unblock conditions. |

## Screenshot Evidence

None: the required rendered ordering is the blocked capability.

## Timings

| Step | Timing |
|---|---:|
| Seven independent overlay cycles | About 8 s total |

## Handoff Notes

- Completed: Every overlay toggle, Home/End opacity, attribution, independence, and final restoration.
- Remaining unfinished coverage: None for HMO_02; terminal BLOCKED only for rendered ordering.
- Blocked or not applicable: ACC_04 canvas capture/inspection.
- State left for the next packet: Route overlays 0/7; heatmap off; GPS tracks/media/points on at 100%.
