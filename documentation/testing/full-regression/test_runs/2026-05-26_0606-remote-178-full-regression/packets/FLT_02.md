# Packet: FLT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_02
- In scope: filter catalog grouping and search behavior.
- Out of scope: applying filter parameters.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_01.
- Required app/data state: Filter panel open with filtering enabled.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: type in filter search and switch catalog groups.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_02 | Viewed the catalog, searched for `year`, cleared search, and selected the Activity group. | Filter catalog browsing, search, and grouping work. | Catalog showed 18 filters grouped as Core, Activity, Date & Time, Performance, and Quality. Searching `year` narrowed results to Date & Time matches; clearing search and selecting Activity showed its four filters. | PASS | `assets/FLT_02-filter-catalog.webp`, `assets/FLT_02-filter-search-year.webp`, `assets/FLT_02-filter-group-activity-cleared.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_02-filter-catalog.webp | Full catalog and group counts after enabling filtering. |
| assets/FLT_02-filter-search-year.webp | Search narrowed catalog to year-related filters. |
| assets/FLT_02-filter-group-activity-cleared.webp | Activity group shows four filters after search is cleared. |

## Timings

| Step | Timing |
|---|---:|
| Catalog/search/group browse | <3m |

## Handoff Notes

- Completed: FLT_02 terminal PASS.
- Remaining unfinished coverage: continue with FLT_03.
- Blocked or not applicable: none.
- State left for the next packet: Filter panel open, Activity group selected, Smart Base Filter currently active.
