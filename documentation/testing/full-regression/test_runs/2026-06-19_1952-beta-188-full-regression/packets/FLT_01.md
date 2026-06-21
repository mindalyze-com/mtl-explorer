# Packet: FLT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_01
- In scope: Verify a previously saved active filter remains active after reload and is surfaced in the Filter panel.
- Out of scope: Full parameter editing coverage, covered by FLT_03 and FLT_04.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_14.
- Required app/data state: 13 visible non-duplicate tracks in the map, with synthetic named tracks available.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: Enable a client-side filter and save a keyword parameter in browser storage.
- Not allowed: Change server data or imported files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_01 | In a clean Chrome context, enabled Filter, selected `Activities by keyword`, entered `synthetic`, reloaded, and reopened Filter. | Previously saved filter remains active and is shown via visible active/chip indicators. | The saved filter persisted after reload: map stayed at `2 / 13 Tracks`, active row remained `Activities by keyword`, keyword input remained `synthetic`, saved config retained `ActivitiesByKeyword` and `SEARCH_WORD=synthetic`, and visible indicators included the Filter alert, tab badge `2`, catalog chips, and `1 active` parameter chip. | PASS | [assets/FLT_01-persisted-active-filter.txt](../assets/FLT_01-persisted-active-filter.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_01-persisted-active-filter.txt](../assets/FLT_01-persisted-active-filter.txt) | Browser context note and saved active filter verification before/after reload. |

## Screenshot Evidence

No screenshot asset was captured for this packet; compact DOM and storage evidence are recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Filter persistence verification | ~18 min |

## Handoff Notes

- Completed: FLT_01.
- Remaining unfinished coverage: FLT_02 onward.
- Blocked or not applicable: In-app browser session hit the known startup-progress failure; isolated Chrome context is being used for browser checks.
- State left for the next packet: Clean Chrome context at `/mtl/filter`; active keyword filter `synthetic` matches 2 of 13 tracks.
