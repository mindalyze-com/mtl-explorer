# Packet: AVR_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: AVR_02
- In scope: virtual race with multiple selected segment tracks, live map movement, ranking/card updates.
- Out of scope: normal map animation tool, covered by AVR_01.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04, MCT_05.
- Required app/data state: Segment Analyzer results with 3 selected Jura-derived tracks.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open Segment Analyzer Race, start/pause/reset virtual race.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_02 | Recreated the 3-track Jura A-B segment, opened Segment Analyzer Race, started playback, and observed the race map mid-run and later. | Multiple racers move together; ranking and racer cards update in real time. | Race opened with `3 racers`, a mini-map, rank cards, and A/B segment. Starting playback moved the racer marker/trail on the map and toggled the control to Pause. The ranking/card area stayed static across ready, running, and later states, with no visible live progress or ranking update. | FAIL | `assets/AVR_02-race-ready.webp`, `assets/AVR_02-race-running.webp`, `assets/AVR_02-race-later.webp`, `assets/AVR_02-race-running.txt`, `assets/AVR_02-race-later.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| AVR-02 | P3 | Virtual Race moves map markers, but racer cards/ranking do not visibly update during playback. | Open Segment Analyzer Race with 3 selected tracks and press Play. | Racer markers move and the ranking/cards reflect live race progress. | The map trail advances and Pause appears, but rank/card content remains static across running and later states. | `assets/AVR_02-race-running.webp`, `assets/AVR_02-race-later.webp` | Users can watch route motion but cannot rely on the card/ranking area for live race status. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/AVR_02-race-ready.webp | Race overlay ready state with 3 racer cards and map. |
| assets/AVR_02-race-running.webp | Race mid-run with active pause control and moving trail. |
| assets/AVR_02-race-later.webp | Later/finished race state showing trail progression. |
| assets/AVR_02-race-running.txt | DOM excerpt from running state. |
| assets/AVR_02-race-later.txt | DOM excerpt from later state. |

## Timings

| Step | Timing |
|---|---:|
| Race setup and playback | <12m |

## Handoff Notes

- Completed: AVR_02 terminal FAIL with issue AVR-02.
- Remaining unfinished coverage: continue with AVR_03.
- Blocked or not applicable: none.
- State left for the next packet: Segment Analyzer Race overlay open after playback, with the map still visible behind it.
