# Packet: AVR_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: AVR_03
- In scope: verify stopping/finishing animation or race leaves map gestures and tools usable.
- Out of scope: normal Animate playback success, covered by AVR_01.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_02.
- Required app/data state: Segment Analyzer Race was opened and playback was started.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: close race/analyzer overlays, pan/zoom map, open/close location search.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_03 | After virtual race playback, closed Race and Segment Analyzer overlays, zoomed the map, dragged/panned it, opened and closed location search. | Stopping or finishing animation/race leaves map gestures and tools usable with no stuck markers/listeners/cursors. | Race overlays closed; map returned to normal state with `10 Tracks`, zoom changed from 30 km to 20 km scale, pan/drag worked, and location search opened/closed normally. | PASS | `assets/AVR_03-after-race-close.webp`, `assets/AVR_03-map-usable.webp`, `assets/AVR_03-map-usable.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/AVR_03-after-race-close.webp | Map immediately after closing race/analyzer overlays. |
| assets/AVR_03-map-usable.webp | Map after zoom/pan and search open/close check. |
| assets/AVR_03-map-usable.txt | DOM excerpt confirming normal map controls and 10-track state. |

## Timings

| Step | Timing |
|---|---:|
| Post-race cleanup usability check | <5m |

## Handoff Notes

- Completed: AVR_03 terminal PASS.
- Remaining unfinished coverage: continue with MED_01.
- Blocked or not applicable: none.
- State left for the next packet: normal map view, no Segment Analyzer or Race overlay open.
