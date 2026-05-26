# Packet: MCT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_01
- In scope: start segment/measure tool, place zones, analyze crossing tracks.
- Out of scope: result click and cleanup, covered by MCT_02/MCT_03.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: imported tracks visible; Lannion track used for zone placement.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: move map/search to Lannion, place temporary segment analyzer zones.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_01 | Opened Segment Analyzer, moved to Lannion, placed A/B zones on the visible Lannion track, and ran Analyze. | Result list of crossing tracks appears with speed/time/distance. | Analyzer showed A/B zones each crossing 1 track; Analyze produced a 1/1 track table with Lannion result, duration `3h 42m`, and A-B speed metric `-1.03`, with metric controls for speed/time/distance. | PASS | `assets/MCT_01-lannion-map.webp`, `assets/MCT_01-zones-placed.webp`, `assets/MCT_01-results.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MCT_01-segments-open.webp | Segment Analyzer initial state. |
| assets/MCT_01-location-search-open.webp | Location search used to move to Lannion. |
| assets/MCT_01-lannion-map.webp | Lannion track visible before analyzer placement. |
| assets/MCT_01-zones-placed.webp | A/B zones on the track. |
| assets/MCT_01-results.webp | Analyzer result table. |

## Timings

| Step | Timing |
|---|---:|
| Segment analyzer setup and run | <8m |

## Handoff Notes

- Completed: MCT_01 terminal PASS.
- Remaining unfinished coverage: continue with MCT_02.
- Blocked or not applicable: none.
- State left for the next packet: Segment Analyzer result table open with one Lannion result.
