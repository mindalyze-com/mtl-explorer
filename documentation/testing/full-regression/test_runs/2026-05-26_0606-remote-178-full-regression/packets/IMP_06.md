# Packet: IMP_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_06
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
| IMP_06 | Verified each imported file by visible name in Stats > Tracks search, mapping, stats summaries, map display, and filter context. | Each imported file appears by name in track browser search, map, statistics summaries, and at least one filter result/context. | Each of the five files mapped to a visible track ID/name; track-browser searches for Jura, Mosel, Vitry, Voie, and Lannion returned one matching row; map showed 5 tracks; stats highlights/recent activity contained the imported names; filter panel opened with the imported 5-track state. | PASS | `assets/IMP_06-imported-track-mapping.txt`, `assets/IMP_06-track-browser-search-results.txt`, `assets/IMP_06-track-list-after-import.webp`, `assets/IMP_05-map-after-freshness-refresh.webp`, `assets/IMP_05-filter-after-import.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_06-imported-track-mapping.txt | Source-to-ID/name mapping. |
| assets/IMP_06-track-browser-search-results.txt | Per-name search results. |
| assets/IMP_06-track-list-after-import.webp | Track browser list after import. |
| assets/IMP_05-filter-after-import.webp | Filter context after import. |

## Timings

| Step | Timing |
|---|---:|
| IMP_06 execution | <1m |

## Handoff Notes

- Completed: IMP_06 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
