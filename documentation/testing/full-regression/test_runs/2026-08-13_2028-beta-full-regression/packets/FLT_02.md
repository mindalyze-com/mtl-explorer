# Packet: FLT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_02.
- In scope: filter catalog groups and search.
- Out of scope: selecting a filter.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_01.
- Required app/data state: Smart Base Filter active.
- Required browser context: Filter overview.

## Allowed Mutations

- Allowed: open catalog and enter/clear search.
- Not allowed: apply a different view.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_02 | Opened Filter view catalog, recorded groups/row count, searched `year`, and canceled. | Catalog browsing, grouping, and search work. | Eighteen views appeared under five groups. Search narrowed to the two relevant year/quarter views with descriptions; cancel preserved the selected view. | PASS | [catalog log](../assets/FLT_02-catalog.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_02-catalog.txt](../assets/FLT_02-catalog.txt) | Baseline groups, row count, search query, results, and cancel state. |

## Screenshot Evidence

Exact catalog labels and counts are recorded as text.

## Timings

| Step | Timing |
|---|---:|
| Catalog open | < 1 s |
| Search update | < 0.5 s |

## Handoff Notes

- Completed: FLT_02.
- Remaining unfinished coverage: FLT_03 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Filter overview open; Smart Base Filter unchanged.

