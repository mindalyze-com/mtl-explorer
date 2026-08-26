# Packet: HMO_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_03
- In scope: Heatmap behavior across a real applied 8->1->8 filter change and final state restoration.
- Out of scope: Other filter semantics already covered by FLT packets.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_01-02; filter baseline reset.
- Required app/data state: Eight matching tracks, heatmap off before the packet.
- Required browser context: Authenticated Map and Filter panels.

## Allowed Mutations

- Allowed: Enable heatmap, select exactly track 100028, reset filter, and restore heatmap off.
- Not allowed: Persist a reduced filter or leave layer changes behind.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_03 | Enabled heatmap, applied exact track 100028 through Criteria, verified global 1/8 and heatmap 4/4/on, reset to 8/8, verified heatmap remained on, then restored it off. | Heatmap visibly updates to filtered tracks and restores with the filter. | Filter and heatmap lifecycle synchronized exactly across 8->1->8 and the baseline was restored. The actual density-shape pixel change is canvas-rendered and cannot be directly captured under ACC_04. | BLOCKED | [assets/HMO_03-filter-sync.txt](../assets/HMO_03-filter-sync.txt); [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

No product issue. The blocked child is rendered heatmap-density proof under ACC_04.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_03-filter-sync.txt](../assets/HMO_03-filter-sync.txt) | Exact 8->1->8 filter and heatmap enabled/restored states. |
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Canvas screenshot constraint and unblock conditions. |

## Screenshot Evidence

None: the required rendered density update is the blocked capability.

## Timings

| Step | Timing |
|---|---:|
| One-track apply settlement | About 1.1 s |
| Filter reset settlement | About 0.65 s |

## Handoff Notes

- Completed: Real selected-track change, global count synchronization, heatmap state across both transitions, and cleanup.
- Remaining unfinished coverage: None for HMO_03; terminal BLOCKED only for density pixels.
- Blocked or not applicable: ACC_04 canvas capture/inspection.
- State left for the next packet: Filter reset at 8/8; heatmap off; route overlays off; other data layers on at 100%.
