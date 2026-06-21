# Packet: FLT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_03
- In scope: Pick a filter, verify parameters appear, parameter edits auto-apply, clearing resets effect, and active chips/count/map/legend/stats stay current.
- Out of scope: Date and geo persistence across reload, covered by FLT_04 and FLT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_02.
- Required app/data state: `Activities by keyword` filter available and active.
- Required browser context: clean isolated Chrome context.

## Allowed Mutations

- Allowed: Temporarily edit and clear the client-side keyword parameter.
- Not allowed: Change server data or imported files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_03 | Changed the `Activities by keyword` KEYWORD parameter to `Jura`, checked filter/map/legend/stats, then cleared the parameter and checked those surfaces again. | Parameters appear; edits auto-apply immediately; clearing resets effect; active chips, visible count, map, legend, and stats reflect current state without stale pending UI. | `Jura` auto-applied to `1 / 13 Tracks`, `LIVE MAP 1 matching tracks`, legend `CYCLING 1`, filter badge `1`, and stats `Showing 1 of 13 tracks`. Clearing the keyword restored `13 / 13 Tracks`, `LIVE MAP 13 matching tracks`, legend `CYCLING 12` / `ON_FOOT 1`, cleared the active parameter indicator, and stats returned to 13 tracks. | PASS | [assets/FLT_03-parameter-auto-apply.txt](../assets/FLT_03-parameter-auto-apply.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_03-parameter-auto-apply.txt](../assets/FLT_03-parameter-auto-apply.txt) | Parameter edit/clear behavior across filter, map, legend, and stats. |

## Screenshot Evidence

No screenshot asset was captured for this packet; compact DOM/storage evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Keyword auto-apply and reset check | ~7 min |

## Handoff Notes

- Completed: FLT_03.
- Remaining unfinished coverage: FLT_04 onward.
- Blocked or not applicable: none.
- State left for the next packet: Filter enabled; keyword parameter cleared; full 13-track visible set restored.
