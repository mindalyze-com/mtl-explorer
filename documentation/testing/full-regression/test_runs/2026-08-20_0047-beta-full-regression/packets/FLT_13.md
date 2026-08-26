# Packet: FLT_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_13
- In scope: Persistence, labeling, and removal of a selected category with no current matches.
- Out of scope: Cross-view filter-switch semantics, covered by FLT_14.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_12.
- Required app/data state: Exact WALKING category available outside a 2010-only range.
- Required browser context: Authenticated Filter criteria and category sheets.

## Allowed Mutations

- Allowed: Select WALKING, set/clear a 2010 date range, and remove the unavailable saved category.
- Not allowed: Change activity values or files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_13 | Make selected WALKING unavailable with a 2010 range, then uncheck it. | Category remains visible as unavailable and is removable. | WALKING stayed checked with an explicit no-match label and unavailable count; unchecking removed it and the unavailable suffix. | PASS | [assets/FLT_13-unavailable-category.txt](../assets/FLT_13-unavailable-category.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_13-unavailable-category.txt](../assets/FLT_13-unavailable-category.txt) | Unavailable label, saved selection, removal, and restoration states. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible labels and checkbox states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Make unavailable, remove, and restore | 4 min |

## Handoff Notes

- Completed: Selected no-match category persistence and removal.
- Remaining unfinished coverage: None for FLT_13.
- Blocked or not applicable: None.
- State left for the next packet: Activities by exact type, no criteria, All 2 categories, nine tracks.
