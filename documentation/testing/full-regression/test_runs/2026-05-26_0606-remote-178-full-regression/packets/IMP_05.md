# Packet: IMP_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_05
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
| IMP_05 | Used the freshness Refresh action and checked map, track list/stats, and filter panel after reload. | Map, track browser, filters, and statistics all show the new data after freshness reload/helper reload. | Freshness refresh returned the app to the map with `5 Tracks`; Stats overview/table showed 5 tracks and totals; Filter panel opened against the 5-track state. | PASS | `assets/IMP_05-freshness-before-refresh.webp`, `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/IMP_05-stats-after-import.webp`, `assets/IMP_05-filter-after-import.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_05-freshness-before-refresh.webp | Freshness banner/panel before refresh. |
| assets/IMP_05-map-after-freshness-refresh.webp | Map after refresh. |
| assets/IMP_05-stats-after-import.webp | Stats overview after import. |
| assets/IMP_05-filter-after-import.webp | Filter panel after import. |

## Timings

| Step | Timing |
|---|---:|
| IMP_05 execution | <1m |

## Handoff Notes

- Completed: IMP_05 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
