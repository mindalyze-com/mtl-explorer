# Packet: IMP_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_09
- In scope: five-GPX import/index/map/stats flow.
- Out of scope: FIT import and delete-two-track flow unless referenced as later prerequisites.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, DAT source packets, IMP_01 baseline.
- Required app/data state: fresh app with public GPX files staged outside watched folder before IMP_02.
- Required browser context: desktop signed-in browser.

## Allowed Mutations

- Allowed: copy GPX files into `./data/gpx`, wait for indexing, use freshness reload, open UI panels, click tracks.
- Not allowed: delete source files or import FIT files in IMP packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_09 | Verified stats/browser totals, direction of totals, activity breakdown, period summaries, rankings, heatmap density, and track-browser summary. | Totals change in the correct direction for distance, duration, ascent/descent, activity, period charts, rankings, heatmap, and browser summary. | Stats increased to 5 tracks, 1,043 km, 23h31m, 4,527 Wh with Bicycle breakdown, rankings/highlights, active periods, and track-browser summary; heatmap rendered density over imported tracks. | PASS | `assets/IMP_05-stats-after-import.webp`, `assets/IMP_06-track-list-after-import.webp`, `assets/IMP_09-heatmap-map-visible.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_05-stats-after-import.webp | Post-import totals, breakdown, rankings, periods. |
| assets/IMP_06-track-list-after-import.webp | Track-browser summary row. |
| assets/IMP_09-heatmap-map-visible.webp | Heatmap density over imported tracks. |

## Timings

| Step | Timing |
|---|---:|
| IMP_09 execution | <1m |

## Handoff Notes

- Completed: IMP_09 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
