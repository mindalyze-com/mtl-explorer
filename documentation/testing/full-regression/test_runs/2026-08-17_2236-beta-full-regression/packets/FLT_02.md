# Packet: FLT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_02
- In scope: Browse filter catalog, group organization, search narrowing, and search reset.
- Out of scope: Selecting/applying a new view covered by FLT_03.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_01.
- Required app/data state: Tracks by year persisted.
- Required browser context: Filter view selector.

## Allowed Mutations

- Allowed: Change and clear catalog search text.
- Not allowed: Apply a different view in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_02 | Browsed the 19-view catalog, searched `duplicate` and `distance`, cleared the search, then cancelled. | Views are grouped; search narrows matching cards/groups; clearing restores the catalog. | Five logical groups rendered. `duplicate` reduced the catalog to Quality / Duplicate tracks; `distance` exposed the matching Performance view; empty search restored five groups and 19 cards. | PASS | [assets/FLT_02-catalog.txt](../assets/FLT_02-catalog.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_02-catalog.txt](../assets/FLT_02-catalog.txt) | Baseline groups/count plus filtered and restored catalog states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered headings, card counts, and search values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Catalog browsing and two searches | About 2 s |
| Clear and restore | Under 1 s |

## Handoff Notes

- Completed: Catalog browsing, grouping, search, and reset.
- Remaining unfinished coverage: None for FLT_02.
- Blocked or not applicable: None.
- State left for the next packet: Filter open with Tracks by year still active.
