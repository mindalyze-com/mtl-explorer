# Packet: TRD_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_07
- In scope: Track-shape thumbnails in browser, filters, stats, related tracks, and selection lists.
- Out of scope: Full screenshot comparison, blocked by ACC_04.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_09, TRD_02, and TRD_03.
- Required app/data state: Nine active tracks with overlapping Bussang sources.
- Required browser context: Authenticated desktop map and sheets.

## Allowed Mutations

- Allowed: Open sheets, scroll visible content, and open transient selection lists.
- Not allowed: Change filters or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_07 | Inspect track previews in filter chooser, Stats recent activity, track browser, Related, and the Bussang overlap chooser. | A small track-shape thumbnail is visible on every required surface. | Rendered preview SVG counts matched the visible rows: filter 9, Stats 5, browser 9, Related 7, and overlap selection 2. | PASS | [assets/TRD_07-shape-previews.txt](../assets/TRD_07-shape-previews.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_07-shape-previews.txt](../assets/TRD_07-shape-previews.txt) | Surface-by-surface rendered SVG counts. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; rendered SVG counts and matching row context are linked above.

## Timings

| Step | Timing |
|---|---:|
| Five surfaces | 7 min |

## Handoff Notes

- Completed: Browser, filter, Stats, Related, and selection-list thumbnails.
- Remaining unfinished coverage: None for TRD_07.
- Blocked or not applicable: None.
- State left for the next packet: Bussang two-track chooser open; source tracks retained.
