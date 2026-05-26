# Packet: FLT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_01
- In scope: filter panel open state and preservation of the active selected filter.
- Out of scope: parameter-specific filtering and reload persistence.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: map loaded with imported tracks; filter state can be enabled.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: enable frontend filtering and reopen the filter panel.
- Not allowed: change source files or track metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_01 | Opened the filter panel, enabled filtering to create an active filter state, closed it, then reopened Filter. | Previously active filter is still active and visible as the current filter/chip. | Reopening Filter kept filtering On, showed the selected Smart Base Filter, and displayed live preview status with 10 matching tracks and 2 categories. | PASS | `assets/FLT_01-filter-open-initial.webp`, `assets/FLT_01-filter-reopened-active.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_01-filter-open-initial.webp | Initial Filter panel state before filter was enabled. |
| assets/FLT_01-filter-reopened-active.webp | Reopened Filter panel with active selected Smart Base Filter. |

## Timings

| Step | Timing |
|---|---:|
| Filter reopen check | <2m |

## Handoff Notes

- Completed: FLT_01 terminal PASS.
- Remaining unfinished coverage: continue with FLT_02.
- Blocked or not applicable: none.
- State left for the next packet: Filter panel open with filtering enabled and Smart Base Filter selected.
