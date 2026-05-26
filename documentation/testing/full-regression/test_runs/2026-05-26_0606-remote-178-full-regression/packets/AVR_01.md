# Packet: AVR_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: AVR_01
- In scope: start normal map animation and verify pause, reset/stop, and speed controls.
- Out of scope: Segment Analyzer virtual race, covered by AVR_02.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01, MCT_05.
- Required app/data state: 10 tracks visible on the map.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open/close Animate sheet and interact with playback controls.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_01 | Opened the Animate tool from the map with 10 tracks visible. | Tracks can play back smoothly; pause, reset/stop, and speed controls work. | Animate opened, but it showed `Tracks 0 / 0`; Play and Stop were disabled while the map header still showed `10 Tracks`. Playback, pause, reset, and speed behavior could not be exercised. | FAIL | `assets/AVR_01-animate-disabled.webp`, `assets/AVR_01-animate-disabled.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| AVR-01 | P2 | Animate tool sees zero tracks and disables playback while tracks are visible on the map. | Open the Animate tool from a map state showing `10 Tracks`. | Animate loads the available track features and enables playback controls. | Animate reports `Tracks 0 / 0`; Play and Stop are disabled. | `assets/AVR_01-animate-disabled.webp` | Users cannot use the normal track animation feature. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/AVR_01-animate-disabled.webp | Animate sheet with disabled controls and `Tracks 0 / 0`. |
| assets/AVR_01-animate-disabled.txt | DOM snapshot excerpt confirming disabled Play/Stop and 10 map tracks. |

## Timings

| Step | Timing |
|---|---:|
| Animate tool check | <4m |

## Handoff Notes

- Completed: AVR_01 terminal FAIL with issue AVR-01.
- Remaining unfinished coverage: continue with AVR_02.
- Blocked or not applicable: none.
- State left for the next packet: Animate sheet remains open; close it before opening Segment Analyzer race if needed.
