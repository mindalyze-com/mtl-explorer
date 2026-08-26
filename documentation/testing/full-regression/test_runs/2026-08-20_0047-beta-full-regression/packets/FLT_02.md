# Packet: FLT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_02
- In scope: Filter catalog browsing, search, and grouping.
- Out of scope: Applying a different view.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_01.
- Required app/data state: Filter catalog available with 19 views.
- Required browser context: Filter view catalog open.

## Allowed Mutations

- Allowed: Search catalog text and clear it.
- Not allowed: Apply a different filter.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_02 | Browse grouped catalog, search `gradient`, search `activity`, then clear. | Catalog search and grouping work. | Five thematic groups and 19 views rendered; search narrowed to four gradient views and the expected activity views; clear restored all views. | PASS | [assets/FLT_02-catalog.txt](../assets/FLT_02-catalog.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_02-catalog.txt](../assets/FLT_02-catalog.txt) | Catalog count, groups, and search results. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible catalog states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Group browse and two searches | 2 min |

## Handoff Notes

- Completed: Catalog grouping, two searches, and clear.
- Remaining unfinished coverage: None for FLT_02.
- Blocked or not applicable: None.
- State left for the next packet: Full catalog restored; Smart Base Filter unchanged.
