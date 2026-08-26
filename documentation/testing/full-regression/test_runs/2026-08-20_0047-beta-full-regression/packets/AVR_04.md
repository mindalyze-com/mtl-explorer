# Packet: AVR_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: AVR_04
- In scope: Virtual-race marker/trail geometry stays on the measured local segment with no global/off-continent line.
- Out of scope: Non-race comparison geometry, covered by MCT_06.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_03 and MCT_06.
- Required app/data state: Three timed racers from the measured Bern A-B segment.
- Required browser context: Embedded Race map.

## Allowed Mutations

- Allowed: Run/reset a second race and inspect the embedded map viewport.
- Not allowed: Use backend geometry probes or private tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_04 | Race three selected local segments and track embedded-map scale/viewport through movement and finish. | Racers/trails stay on real segment with no world/[0,0]/South Africa geometry. | Race map stayed at 30-50 m during movement in an 896x617 local viewport; three cards progressed normally and no world-scale refit/off-continent state occurred. | PASS | [assets/AVR_04-race-geometry.txt](../assets/AVR_04-race-geometry.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_04-race-geometry.txt](../assets/AVR_04-race-geometry.txt) | Known fixture bounds, race map scale/geometry, and racer progress. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; local viewport/scale and racer-card evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Recreate and run geometry race | 2 min |
| Reset and close | <1 min |

## Handoff Notes

- Completed: Virtual-race local geometry regression.
- Remaining unfinished coverage: None for AVR_04.
- Blocked or not applicable: Pixel screenshot unavailable; local race viewport and progress passed.
- State left for the next packet: Race reset/closed; Segment Analyzer remains behind it and should be stopped before Media.
