# Packet: AVR_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: AVR_03.
- In scope: map and tool usability after race completion/close.
- Out of scope: race geometry locality, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_02.
- Required app/data state: completed two-racer A-A race.
- Required browser context: desktop map.

## Allowed Mutations

- Allowed: close race, zoom map, and open Filter.
- Not allowed: alter filter or stored tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_03 | Let both racers finish, closed race/analyzer, zoomed the map, and opened Filter. | Finish/stop leaves map gestures and tools usable with no stuck state. | Both reached 100%; closing restored the normal map, Zoom in changed 100 m→50 m, and Filter opened with the correct 12-track state. | PASS | [cleanup check](../assets/AVR_03-cleanup.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_03-cleanup.txt](../assets/AVR_03-cleanup.txt) | Finish, close, zoom, and tool-open observations. |

## Screenshot Evidence

No screenshot was needed; exact visible states and the scale transition are recorded in the compact text evidence.

## Timings

| Step | Timing |
|---|---:|
| Race finish | 12 s configured |
| Post-close zoom | 0.45 s |
| Filter open | 0.45 s |

## Handoff Notes

- Completed: AVR_03 is terminal `PASS`.
- Remaining unfinished coverage: AVR_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: normal desktop map at 50 m scale; Filter sheet open.
