# Packet: TRD_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_01
- In scope: opening one GPX-backed and one FIT-backed track from user-facing navigation and recording IDs/source filenames.
- Out of scope: per-tab detail behavior, covered by TRD_02-TRD_14.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07, FIT_03, MAP_11.
- Required app/data state: GPX and FIT tracks imported.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open existing tracks.
- Not allowed: change track metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | Opened GPX-backed Jura track and FIT-backed Activity track from user-facing navigation. | At least one GPX-backed and one FIT-backed track open; IDs/source filenames are recorded. | GPX-backed track #100001 (`JuraRoute72011.gpx`) opened from the map; FIT-backed track #100005 (`Activity.fit`) opened from track browser/detail flow. | PASS | `assets/MAP_11-selected-track-detail.webp`, `assets/FIT_03-fit-detail-overview.webp`, `assets/IMP_06-imported-track-mapping.txt`, `assets/FIT_02-track-list-search-fit.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_11-selected-track-detail.webp | GPX-backed detail for #100001. |
| assets/FIT_03-fit-detail-overview.webp | FIT-backed detail for #100005. |
| assets/IMP_06-imported-track-mapping.txt | GPX source-to-track mapping. |
| assets/FIT_02-track-list-search-fit.txt | FIT track row/source evidence. |

## Timings

| Step | Timing |
|---|---:|
| Detail opening evidence | Reused from IMP/FIT/MAP packets |

## Handoff Notes

- Completed: TRD_01 terminal PASS.
- Remaining unfinished coverage: continue with TRD_02.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
