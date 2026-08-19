# Packet: AVR_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_03
- In scope: Post-animation/race cleanup and map/tool usability.
- Out of scope: Race progress and geometry.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_02.
- Required app/data state: Paused two-racer Race.
- Required browser context: Desktop Race above Segment Analyzer.

## Allowed Mutations

- Allowed: Reset/close temporary UI, zoom, and open Map settings.
- Not allowed: Persist map-setting changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_03 | Reset the race, closed Race/results, zoomed the map, and opened Map settings. | Stopping/finishing leaves map gestures/tools usable with no stuck state. | Both cards reset to 0%; all race/analyzer UI disappeared; Zoom In changed 100→50 m; Map settings opened with normal controls and 13 tracks remained. | PASS | [assets/AVR_03-post-race-usability.txt](../assets/AVR_03-post-race-usability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_03-post-race-usability.txt](../assets/AVR_03-post-race-usability.txt) | Reset, close, zoom, tool-open, and clean-state assertions. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible control/state changes provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Reset and close | About 2 s |
| Zoom and Map tool check | About 1 s |

## Handoff Notes

- Completed: Post-race cleanup and usability.
- Remaining unfinished coverage: None for AVR_03.
- Blocked or not applicable: None.
- State left for the next packet: Map settings open; no race state.

