# Packet: MCT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MCT_03.
- In scope: temporary marker and map-click cleanup when measurement stops.
- Out of scope: persisted result display and comparison content.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_02.
- Required app/data state: Lannion map and prior measurement result.
- Required browser context: signed-in desktop map.

## Allowed Mutations

- Allowed: toggle the measure tool and click the map after stop.
- Not allowed: save or delete user data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_03 | Opened the placement tool, toggled it off, and clicked the map after stop. | Stopping measure removes temporary markers and listeners. | Placement guidance disappeared; the post-stop map click created no zone marker, zone count, or guidance. The prior read-only result sheet remained available. | PASS | [cleanup check](../assets/MCT_03-cleanup.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_03-cleanup.txt](../assets/MCT_03-cleanup.txt) | Exact open, stop, and post-stop control sequence. |

## Screenshot Evidence

No screenshot was needed; the durable text evidence records the negative listener assertion and visible state transitions.

## Timings

| Step | Timing |
|---|---:|
| Open/stop | 0.5 s each |
| Post-stop observation | 0.4 s |

## Handoff Notes

- Completed: MCT_03 is terminal `PASS`.
- Remaining unfinished coverage: MCT_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: result sheet open with five of seven tracks selected; measurement placement inactive.
