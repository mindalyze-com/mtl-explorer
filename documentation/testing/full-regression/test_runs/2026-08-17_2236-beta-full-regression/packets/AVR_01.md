# Packet: AVR_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_01
- In scope: Standalone Animate on desktop/mobile and all transport/settings controls.
- Out of scope: Segment virtual race.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_06.
- Required app/data state: 13 visible tracks.
- Required browser context: Authenticated desktop and mobile viewports.

## Allowed Mutations

- Allowed: Temporary date range/speed and animation transport state.
- Not allowed: Persist track changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_01 | Exercised open/play/collapse/pause/resume/stop/expand/reset, date range, duration, and speed on desktop; repeated the map-first transport path at 390x844. | Existing tracks remain until playback; controls work on desktop/mobile. | Map stayed at 13 tracks before playback; 20 ms and 1000 ms durations updated correctly; progress paused/resumed 3→4/13; date range narrowed to one track; stop/reset restored ready state; mobile paused at 2/13 and resumed/stopped correctly. | PASS | [assets/AVR_01-animation-controls.txt](../assets/AVR_01-animation-controls.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_01-animation-controls.txt](../assets/AVR_01-animation-controls.txt) | Desktop/mobile state transitions and exact counts, dates, speed, and duration. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible control/status transitions provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Desktop full control pass | About 12 s |
| Mobile transport pass | About 5 s |

## Handoff Notes

- Completed: Standalone animation on desktop and mobile.
- Remaining unfinished coverage: None for AVR_01.
- Blocked or not applicable: None.
- State left for the next packet: Desktop map, animation closed.

