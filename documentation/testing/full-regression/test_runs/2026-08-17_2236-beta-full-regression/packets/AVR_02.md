# Packet: AVR_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_02
- In scope: Multi-racer virtual-race progress, ranking, and cards.
- Out of scope: Post-race cleanup and geometry bounds.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_01 and MCT_04.
- Required app/data state: A/B Bern comparison with five selections.
- Required browser context: Authenticated desktop Race sheet.

## Allowed Mutations

- Allowed: Temporary analyzer/race state and playback.
- Not allowed: Track changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_02 | Opened Race for five selections, ran the two valid racers for two samples, then paused. | Multiple racers move together; ranking and cards update in real time. | Two racers advanced together from 0/0% to 32/14% and then 100/51%; distance, rank, and card values updated, and pause preserved progress. Three invalid selections were explicitly skipped. | PASS | [assets/AVR_02-virtual-race.txt](../assets/AVR_02-virtual-race.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_02-virtual-race.txt](../assets/AVR_02-virtual-race.txt) | Racer set, progress samples, ranking, map scale, and pause state. |

## Screenshot Evidence

Unavailable under ACC_04. Exact card/rank/progress DOM provides direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Recreate analyzer and open Race | About 5 s |
| Play and sample/pause | About 5 s |

## Handoff Notes

- Completed: Multi-racer virtual race.
- Remaining unfinished coverage: None for AVR_02.
- Blocked or not applicable: None.
- State left for the next packet: Race paused at 100%/51%.

