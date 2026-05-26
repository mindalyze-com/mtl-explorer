# Packet: MCT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_04
- In scope: select several segment-crossing tracks, open Segment Analyzer comparison, verify chart and comparison map.
- Out of scope: sub-track endpoint validation, covered by MCT_05.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01, MCT_02, MCT_03.
- Required app/data state: imported Jura-derived tracks visible.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: move map, place temporary A/B zones, adjust radius, open comparison overlay.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_04 | Opened Segment Analyzer over the Jura route, placed A/B zones, increased radius to 13,000 m to include the shared Jura segment, analyzed 3 shared tracks, selected all results, and opened Compare. | Segment comparison for several tracks shows comparison charts and a map aligned to the selected segment, including tracks with missing data. | Analyzer found 3/3 tracks (`JuraRoute72011.gpx`, `.igc`, `.nmea`) and Compare opened with racer cards plus speed/altitude charts. The required comparison mini-map did not render as a usable map: the MapLibre canvas existed but its `.sc-minimap`/wrapper height was `0`/collapsed, leaving no visible segment map to verify alignment. | FAIL | `assets/MCT_04-compare-top.webp`, `assets/MCT_04-compare-charts.webp`, `assets/MCT_04-canvas-bounds.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MCT-01 | P2 | Segment comparison mini-map collapses while charts render. | Open Segment Analyzer, analyze a shared A/B segment with 3 tracks, then click Compare. | The comparison view shows a visible mini-map aligned with the selected segment and comparison charts. | Racer cards and charts render, but the comparison mini-map wrapper collapses to no usable height despite a MapLibre canvas being present. | `assets/MCT_04-compare-top.webp`, `assets/MCT_04-canvas-bounds.txt` | Users cannot visually verify segment alignment in comparison mode. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/MCT_04-compare-top.webp | Compare overlay top with 3 selected tracks, segment A-B, and racer cards. |
| assets/MCT_04-compare-charts.webp | Compare overlay after scroll showing speed and altitude charts. |
| assets/MCT_04-canvas-bounds.txt | Canvas/wrapper bounds showing collapsed comparison mini-map. |

## Timings

| Step | Timing |
|---|---:|
| Segment comparison setup and verification | <15m |

## Handoff Notes

- Completed: MCT_04 terminal FAIL with issue MCT-01.
- Remaining unfinished coverage: continue with MCT_05.
- Blocked or not applicable: none.
- State left for the next packet: Segment Analyzer Compare overlay open for the A-B Jura segment with 3 selected tracks.
