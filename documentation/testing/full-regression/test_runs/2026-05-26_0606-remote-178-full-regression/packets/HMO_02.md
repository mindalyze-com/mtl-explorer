# Packet: HMO_02

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: HMO_02
- In scope: Toggle map overlays independently, verify opacity sliders, and verify overlay ordering does not hide GPS tracks.
- Out of scope: Heatmap filter updates.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_01.
- Required app/data state: Track geometry visible at Lannion and Delémont; map settings available.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Change overlay layer toggles and opacity settings.
- Not allowed: Change track/media data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_02 | In Lannion, toggled worldwide Hiking/Cycling/MTB overlays independently and moved opacity sliders. In Delémont, toggled Swiss Hiking Routes/Bike Routes/MTB Routes/Hiking Trails independently and moved opacity sliders for representative Swiss overlays. | Each overlay can be toggled independently; opacity sliders work; overlay ordering keeps tracks usable/visible. | Worldwide and Swiss overlay controls switched independently. Opacity handles moved for worldwide and Swiss overlays. Waymarked Trails and Swiss overlays rendered without making the visible GPS track layer disappear. | PASS | assets/HMO_02-worldwide-overlays.webp; assets/HMO_02-swiss-overlays.webp; assets/HMO_02-overlay-states.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/HMO_02-worldwide-overlays.webp | Worldwide route overlays enabled at Lannion with GPS track still visible. |
| assets/HMO_02-swiss-overlays.webp | Swiss route/trail overlays enabled at Delémont with GPS track still visible. |
| assets/HMO_02-overlay-states.txt | Toggle/opacity state summary for worldwide and Swiss overlay controls. |

## Timings

| Step | Timing |
|---|---:|
| Worldwide and Swiss overlay checks | ~12 min |

## Handoff Notes

- Completed: HMO_02.
- Remaining unfinished coverage: HMO_03 onward.
- Blocked or not applicable: None.
- State left for the next packet: Delémont viewport; Swiss overlays enabled; worldwide overlays off; heatmap off.
