# Packet: FLT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_04
- In scope: date, text, and geo filter parameter persistence after reload.
- Out of scope: full geo draw control matrix, covered by FLT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03.
- Required app/data state: filtering enabled with `Activities by keyword` selected.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: set filter parameters and reload the browser tab.
- Not allowed: mutate track source files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_04 | Set keyword `Jura`, selected a From date of `01.01.2010 07:32:53`, drew a rectangle geo parameter, then reloaded and reopened Filter/Base scope. | Date, text, and geo parameters save and re-apply correctly after reload. | The text keyword and date parameter persisted and re-applied after reload. The rectangle geo parameter was visible before reload but disappeared afterward; Base scope showed only 1 active parameter and the Area section returned to empty Draw buttons. | FAIL | `assets/FLT_04-date-text-geo-set.webp`, `assets/FLT_04-after-geo-reload-map.webp`, `assets/FLT_04-after-geo-reload-expanded.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-02 | P2 | Geo filter parameter is lost after reload. | Enable Filter, choose `Activities by keyword`, expand Base scope, draw a rectangle geo area, reload, reopen Base scope. | The rectangle area remains active and is re-applied after reload. | Before reload, Rectangle showed coordinates and Base scope had 2 active parameters; after reload, Area had no active rectangle and Base scope dropped to 1 active parameter. | `assets/FLT_04-date-text-geo-set.webp`, `assets/FLT_04-after-geo-reload-expanded.webp` | Users can lose spatial filtering after reload without an explicit warning. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_04-date-text-geo-set.webp | Keyword, date, and rectangle area active before reload. |
| assets/FLT_04-after-geo-reload-map.webp | Reloaded map still filtered to 0/10 from persisted non-geo parameters. |
| assets/FLT_04-after-geo-reload-expanded.webp | Reopened Base scope after reload with date/text persisted but geo area missing. |

## Timings

| Step | Timing |
|---|---:|
| Date/text/geo persistence check | <8m |

## Handoff Notes

- Completed: FLT_04 terminal FAIL due lost geo parameter after reload.
- Remaining unfinished coverage: continue with FLT_05.
- Blocked or not applicable: none.
- State left for the next packet: filter active with keyword `Jura` and From date persisted; geo area is not active.
