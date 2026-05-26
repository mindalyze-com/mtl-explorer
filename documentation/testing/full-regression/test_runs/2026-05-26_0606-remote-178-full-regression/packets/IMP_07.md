# Packet: IMP_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_07
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
| IMP_07 | Clicked visible map lines for all five imported GPX tracks; used the overlap selection list for Mosel/VoieVerte. | Map zoom/click selection opens details, point/selection UI is usable, geometry is visible, and no stale/duplicate lines appear. | CUA viewport clicks opened Lannion #100003, Vitry #100000, Jura #100001 directly; overlapping Mosel/VoieVerte click produced a two-track selection list and each row opened #100002/#100004. No stale or duplicate lines were visible in captured map evidence. | PASS | `assets/IMP_07-map-click-results.txt`, `assets/IMP_07-cua-click-lannion.webp`, `assets/IMP_07-map-click-vitry.webp`, `assets/IMP_07-map-click-jura.webp`, `assets/IMP_07-map-selection-mosel.webp`, `assets/IMP_07-map-selection-voieverte.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/IMP_07-map-click-results.txt | Coordinate click and opened track IDs. |
| assets/IMP_07-cua-click-lannion.webp | Lannion map click opened details. |
| assets/IMP_07-map-click-vitry.webp | Vitry map click opened details. |
| assets/IMP_07-map-click-jura.webp | Jura map click opened details. |
| assets/IMP_07-map-selection-mosel.webp | Overlap selection opened Mosel. |
| assets/IMP_07-map-selection-voieverte.webp | Overlap selection opened VoieVerte. |

## Timings

| Step | Timing |
|---|---:|
| IMP_07 execution | 5m |

## Handoff Notes

- Completed: IMP_07 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks imported and visible; FIT not imported yet; no deletion performed.
