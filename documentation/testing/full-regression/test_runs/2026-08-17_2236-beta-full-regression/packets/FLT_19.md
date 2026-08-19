# Packet: FLT_19

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_19
- In scope: Pause/resume synchronization across result, map, statistics, persistence, and desktop/mobile switch placement.
- Out of scope: Filter editing covered by earlier FLT packets.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_18.
- Required app/data state: Saved Tracks by year setup with only 2013 selected.
- Required browser context: Primary desktop tab plus temporary 390x844 mobile tab.

## Allowed Mutations

- Allowed: Toggle Apply filter, reload, open Statistics, and temporarily override viewport.
- Not allowed: Change the saved filter setup or leave a viewport override active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_19 | Paused and resumed the saved filter on desktop and mobile, checked map/statistics, reloaded both states, and counted Apply filter switches. | Result status, map, statistics, persisted state, and switch remain synchronized; exactly one switch appears. | Paused state consistently showed all 15 tracks and retained the setup; resumed state consistently showed 1/15 and 2013. Statistics changed from 15 tracks/1,048 km to 1 track/25.9 km. Reload preserved the switch state. Both layouts exposed exactly one Apply filter switch. | PASS | [assets/FLT_19-pause-resume.txt](../assets/FLT_19-pause-resume.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_19-pause-resume.txt](../assets/FLT_19-pause-resume.txt) | Desktop/mobile switch, result, map, statistics, persistence, and cleanup evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible states, counts, viewport dimensions, map labels, and statistics provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Desktop pause/resume and statistics | About 8 s |
| Desktop persistence reload | About 3 s |
| Mobile pause/resume and persistence | About 6 s |

## Handoff Notes

- Completed: Desktop/mobile pause, resume, persistence, result, map, statistics, and duplicate-action checks.
- Remaining unfinished coverage: None for FLT_19.
- Blocked or not applicable: None.
- State left for the next packet: Saved 2013-only filter is resumed; primary tab remains on Filter; mobile tab is closed and viewport reset.
