# Packet: AVR_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_03
- In scope: Map gestures/tools remain usable after animation/race stop or finish.
- Out of scope: Racer geometry bounds.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_02.
- Required app/data state: Animation stop/finish already exercised; virtual race finished.
- Required browser context: Desktop map.

## Allowed Mutations

- Allowed: Reset/close race, zoom map, and open Filter.
- Not allowed: Leave replay controls active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_03 | Reset/close finished race, zoom in/out, and open Filter; correlate with earlier animation stop/finish transitions. | Map gestures and tools remain usable with no stuck replay state. | Race reset to 0%, map scale changed 100 m -> 50 m -> 100 m, and Filter opened normally; animation had also transitioned directly to Segments after stop/finish. | PASS | [assets/AVR_03-post-playback-usability.txt](../assets/AVR_03-post-playback-usability.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_03-post-playback-usability.txt](../assets/AVR_03-post-playback-usability.txt) | Reset, zoom, and tool-navigation evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible gesture/control state is linked above.

## Timings

| Step | Timing |
|---|---:|
| Reset/close/zoom/tool check | 1 min |

## Handoff Notes

- Completed: Post-animation and post-race map usability.
- Remaining unfinished coverage: None for AVR_03.
- Blocked or not applicable: None.
- State left for the next packet: Filter open; race reset and closed.
