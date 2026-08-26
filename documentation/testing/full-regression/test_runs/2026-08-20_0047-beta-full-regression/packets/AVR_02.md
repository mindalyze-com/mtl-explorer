# Packet: AVR_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_02
- In scope: Multi-racer virtual race with live ranks/cards.
- Out of scope: Post-stop gesture cleanup and exact geometry sanity.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_01 and MCT_01.
- Required app/data state: Recreated four-track A-B segment result; three timed racers and one insufficient-data row.
- Required browser context: Segment Analyzer Race view.

## Allowed Mutations

- Allowed: Start and finish one virtual race.
- Not allowed: Fabricate a racer for an insufficient-data track.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_02 | Start A-B race at 80x and sample racer cards through completion. | Multiple racers move together; rank and card progress update live. | Three racers advanced independently from 0 to 100%; ranks/cards showed changing percent/distance, while the untimed fourth row was explicitly skipped. | PASS | [assets/AVR_02-virtual-race.txt](../assets/AVR_02-virtual-race.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_02-virtual-race.txt](../assets/AVR_02-virtual-race.txt) | Setup, sampled live progress, ranks, and finish values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible live card/rank values are linked above.

## Timings

| Step | Timing |
|---|---:|
| Recreate race result | 2 min |
| Run and sample race | 1 min |

## Handoff Notes

- Completed: Three-racer live race and finish.
- Remaining unfinished coverage: None for AVR_02.
- Blocked or not applicable: sample.geojson correctly skipped for insufficient timed segment data.
- State left for the next packet: Race complete at 100%; start/reset controls available.
