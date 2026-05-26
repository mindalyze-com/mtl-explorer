# Packet: FLT_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_08
- In scope: clearing/disabling the active filter and restoring all tracks.
- Out of scope: deleting saved filter definitions.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_07.
- Required app/data state: filter enabled with all legend groups restored.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: turn filtering off.
- Not allowed: delete track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_08 | Opened Filter and turned the filter switch Off. | Clearing the filter restores all tracks. | The map returned to `10 Tracks`, showed a `Showing all tracks` alert, removed the active legend, disabled Colors/SQL tabs, and displayed the `Filtering is off` panel. | PASS | `assets/FLT_08-filter-before-disable.webp`, `assets/FLT_08-filter-disabled-map.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_08-filter-before-disable.webp | Filter enabled with all tracks visible before clearing. |
| assets/FLT_08-filter-disabled-map.webp | Filter disabled and normal all-track map restored. |

## Timings

| Step | Timing |
|---|---:|
| Filter clear/disable | <2m |

## Handoff Notes

- Completed: FLT_08 terminal PASS.
- Remaining unfinished coverage: continue with TBS_01.
- Blocked or not applicable: none.
- State left for the next packet: filtering off; map restored to all 10 visible tracks.
