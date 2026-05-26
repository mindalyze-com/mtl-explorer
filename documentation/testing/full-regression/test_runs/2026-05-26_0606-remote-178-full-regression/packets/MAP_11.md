# Packet: MAP_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_11
- In scope: clicking a point/line on a selected track map to show point metrics.
- Out of scope: overview aggregate metrics, covered in TRD.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09/MAP_10.
- Required app/data state: selected Jura GPX-backed track detail open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click visible track line/point areas in the detail mini-map.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_11 | Opened track #100001 from the map and clicked multiple visible line/point locations in the detail map. | Clicking a point on a track shows a popup with metrics such as time, speed, and elevation. | Track details and aggregate metrics were visible, but no point popup appeared after multiple line/point clicks. | FAIL | `assets/MAP_11-selected-track-detail.webp`, `assets/MAP_11-point-popup.webp`, `assets/MAP_11-point-popup-retry.webp`, `assets/MAP_11-point-popup-retry.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MAP-02 | P3 | Track point clicks do not expose a point metrics popup in the tested detail map. | Open track #100001 from the map, then click visible line/point positions in the detail mini-map. | A point popup shows time/speed/elevation or equivalent point metrics. | No point popup appeared; detail overview aggregate metrics stayed visible. | `assets/MAP_11-point-popup-retry.webp`, `assets/MAP_11-point-popup-retry.txt` | Users cannot inspect exact point metrics from the map in this flow. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_11-selection-before-point.webp | Overlap list before selecting the test track. |
| assets/MAP_11-selected-track-detail.webp | Selected track detail with mini-map. |
| assets/MAP_11-point-popup.webp | First point-click attempt. |
| assets/MAP_11-point-popup-retry.webp | Repeated point-click attempts. |
| assets/MAP_11-point-popup-retry.txt | Popup detection summary. |

## Timings

| Step | Timing |
|---|---:|
| Point popup attempts | <2m |

## Handoff Notes

- Completed: MAP_11 terminal FAIL.
- Remaining unfinished coverage: continue with MAP_12.
- Blocked or not applicable: none.
- State left for the next packet: track detail remained open in the in-app browser; no data changed.
