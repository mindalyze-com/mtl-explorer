# Packet: FLT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_06
- In scope: live updates to visible count, map legend/colors, and statistics after changing the active filter.
- Out of scope: filter persistence after reload.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: filtering enabled with keyword/date parameters available.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: edit filter parameters and switch between Filter/Stats views.
- Not allowed: reload the page during the live-update check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_06 | Cleared the date lower bound to change the active keyword filter from 0/10 to 1/10, opened Stats, then cleared the keyword to restore 10/10 and reopened Stats. | Applied filter updates visible count, map colors, legend, and stats without full page reload. | The map count and legend changed immediately to 1/10 with one Bicycle category; Stats showed `Showing 1 of 10 tracks`. Clearing the keyword changed the map/legend to 10/10 with Bicycle and Walking categories, and Stats updated to 10 tracks and all-track totals without reload. | PASS | `assets/FLT_06-date-cleared-map.webp`, `assets/FLT_06-stats-filtered.webp`, `assets/FLT_06-keyword-cleared-map.webp`, `assets/FLT_06-stats-all-after-clear-retry.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_06-date-cleared-map.webp | Filtered map/legend at 1/10 tracks after changing parameters. |
| assets/FLT_06-stats-filtered.webp | Statistics panel scoped to the 1-track filter. |
| assets/FLT_06-keyword-cleared-map.webp | Map/legend updated to 10/10 after clearing keyword. |
| assets/FLT_06-stats-all-after-clear-retry.webp | Statistics panel updated to all 10 tracks without reload. |

## Timings

| Step | Timing |
|---|---:|
| Live filter update check | <4m |

## Handoff Notes

- Completed: FLT_06 terminal PASS.
- Remaining unfinished coverage: continue with FLT_07.
- Blocked or not applicable: none.
- State left for the next packet: filtering enabled with `Activities by keyword` selected but no active keyword/date/geo parameters.
