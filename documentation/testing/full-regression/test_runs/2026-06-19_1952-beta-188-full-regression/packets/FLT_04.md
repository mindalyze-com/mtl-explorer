# Packet: FLT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_04
- In scope: Date, text, and geo parameters save to client state, survive reload, and re-apply to the visible track result.
- Out of scope: Exhaustive circle/rectangle/polygon drawing behavior, covered by FLT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: `Activities by keyword` selected; full 13-track visible set restored before edits.
- Required browser context: clean isolated Chrome context because the in-app browser is already captured as affected by the SGN_07 startup-progress failure.

## Allowed Mutations

- Allowed: Temporarily edit client-side filter date, text, and geo parameters; reset those client-side parameters after evidence capture.
- Not allowed: Change server data or imported files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_04 | Set inherited date range controls to `2021-01-01 00:00` through `2026-12-31 23:59`, set keyword to `synthetic`, drew a circle through the map drawing toolbar, reloaded, and inspected both visible controls and persisted state. | Date, text, and geo parameters persist after reload and re-apply to the track count/map filter state. | Date and keyword controls auto-applied to `2 / 13 Tracks`; drawing the circle then narrowed the result to `0 / 13 Tracks`. After reload, visible controls still showed the same dates/times and keyword, persisted state contained `SEARCH_WORD`, `DATE_TIME_FROM`, `DATE_TIME_TO`, and `GEO_CIRCLE_1`, and the result stayed `0 / 13 Tracks`. Temporary parameters were then reset and the full `13 / 13 Tracks` set was restored for FLT_05. | PASS | [assets/FLT_04-date-text-geo-persistence.txt](../assets/FLT_04-date-text-geo-persistence.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_04-date-text-geo-persistence.txt](../assets/FLT_04-date-text-geo-persistence.txt) | Date/text/geo setup, post-reload persisted parameter object, and reset state. |

## Screenshot Evidence

No screenshot asset was captured for this packet; compact DOM/storage evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Date/text/geo persistence and reset check | ~12 min |

## Handoff Notes

- Completed: FLT_04.
- Remaining unfinished coverage: FLT_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: Filter page open with `Activities by keyword` selected; client-side date/text/geo parameter maps reset; full `13 / 13 Tracks` visible set restored.
