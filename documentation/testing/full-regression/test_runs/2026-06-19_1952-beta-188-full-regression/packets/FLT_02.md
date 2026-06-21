# Packet: FLT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_02
- In scope: Verify filter catalog browsing, grouping, and search.
- Out of scope: Editing filter parameters, covered by FLT_03 and FLT_04.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_01.
- Required app/data state: Filter panel enabled with catalog available.
- Required browser context: clean isolated Chrome context at `/mtl/filter`.

## Allowed Mutations

- Allowed: Change catalog group chip and search text.
- Not allowed: Change saved filter selection or server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_02 | Browsed catalog group chips, selected `Activity 4`, searched `distance`, and cleared the search. | Catalog grouping and search work. | Catalog showed `All 18`, `Core 1`, `Activity 4`, `Date & Time 5`, `Performance 4`, `Quality 4`; selecting `Activity 4` showed only four activity rows; searching `distance` narrowed to two performance rows; clearing search restored the full grouped catalog. | PASS | [assets/FLT_02-catalog-search.txt](../assets/FLT_02-catalog-search.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_02-catalog-search.txt](../assets/FLT_02-catalog-search.txt) | Catalog chips, group rows, search result, and clear-search evidence. |

## Screenshot Evidence

No screenshot asset was captured for this packet; compact DOM evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Catalog grouping/search check | ~4 min |

## Handoff Notes

- Completed: FLT_02.
- Remaining unfinished coverage: FLT_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: Catalog search cleared; active keyword filter `synthetic` remains in place.
