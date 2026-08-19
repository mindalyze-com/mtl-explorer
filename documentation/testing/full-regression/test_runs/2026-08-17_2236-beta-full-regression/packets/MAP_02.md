# Packet: MAP_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_02
- In scope: Visible map track inventory and count consistency.
- Out of scope: Duplicate source records intentionally excluded from the current visible result.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: Settled root map and default applied filter.
- Required browser context: Signed in with current data revision loaded.

## Allowed Mutations

- Allowed: Open Filter and its read-only Review Tracks table.
- Not allowed: Change filter criteria, categories, or tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compared the map count, default filter count, Review Tracks summary, full table row count, and pagination. | All current tracks appear and total/visible count is correct. | Map, filter, review summary, and the complete one-page inventory all report 14 visible tracks; the table contains exactly 14 data rows and no console errors were captured. | PASS | [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_02-track-count.txt](../assets/MAP_02-track-count.txt) | Cross-surface count and complete row-inventory evidence. |

## Screenshot Evidence

Not available because ACC_04 blocks screenshots; semantic table and control evidence was used.

## Timings

| Step | Timing |
|---|---:|
| Open filter and Review Tracks | Under 5 s |
| Count complete table inventory | Under 1 s |

## Handoff Notes

- Completed: All-current-track inventory and count consistency.
- Remaining unfinished coverage: None for MAP_02.
- Blocked or not applicable: None.
- State left for the next packet: Review Tracks panel open over the unchanged map.
