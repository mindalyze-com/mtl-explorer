# Packet: AVR_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_04
- In scope: Virtual-race marker/trail GPS geometry sanity.
- Out of scope: General race controls and cleanup.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04-MCT_06 and AVR_02.
- Required app/data state: Two valid Bern-area A-B racers.
- Required browser context: Running desktop Race mini-map.

## Allowed Mutations

- Allowed: Recreate temporary A/B race and read track geometry.
- Not allowed: Modify tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_04 | Ran the two valid measured-segment racers, sampled progress/map scale, and audited both live source geometries. | Racer markers/trails remain on the real segment with no global, `[0,0]`, or off-continent jump. | The mini-map fit from 500 m to 50 m as both racers advanced; all source points stayed in lng 7.4468..7.4490 / lat 46.9475..46.9492 with sub-0.001° steps and no zero/off-continent coordinate. | PASS | [assets/AVR_04-race-geometry.txt](../assets/AVR_04-race-geometry.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_04-race-geometry.txt](../assets/AVR_04-race-geometry.txt) | Running racer progress, mini-map scales, per-track bounds, and sanity assertions. |

## Screenshot Evidence

Unavailable under ACC_04. Live geometry plus the accessible 50 m race-map scale provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Recreate/open Race | About 6 s |
| Playback and geometry audit | About 3 s |

## Handoff Notes

- Completed: Virtual-race GPS geometry regression.
- Remaining unfinished coverage: None for AVR_04.
- Blocked or not applicable: None.
- State left for the next packet: Race running; close/reset before media coverage.

