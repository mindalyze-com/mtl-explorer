# Packet: FLT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_03
- In scope: selecting a filter, parameter display, apply/reset/cancel behavior.
- Out of scope: date and geo parameter persistence.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_02.
- Required app/data state: Filter panel open with filtering enabled.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: select a filter and edit/reset its frontend parameters.
- Not allowed: mutate track source files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_03 | Selected `Activities by keyword`, entered `Jura`, then cleared the keyword field. | Picking a filter reveals parameters; apply, reset, and cancel all behave correctly. | Selecting the filter revealed a `Keyword` parameter. Entering `Jura` live-applied the filter and reduced the map/live preview to 1/10 tracks; clearing the field reset the preview to 10/10. No explicit Apply or Cancel controls were exposed in the tested UI. | FAIL | `assets/FLT_03-keyword-params.webp`, `assets/FLT_03-keyword-applied.webp`, `assets/FLT_03-keyword-reset-attempt.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-01 | P3 | Filter parameter workflow lacks explicit Apply/Cancel controls. | Open Filter, choose `Activities by keyword`, enter a keyword, and scan the selected-filter panel/status area. | The filter parameter workflow provides apply, reset, and cancel controls as described by the regression plan. | Live preview applies changes and clearing the field resets them, but no explicit Apply or Cancel controls are visible. | `assets/FLT_03-keyword-params.webp`, `assets/FLT_03-keyword-applied.webp`, `assets/FLT_03-keyword-reset-attempt.webp` | Users relying on confirm/cancel semantics may unintentionally change the active filter state. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/FLT_03-keyword-params.webp | Keyword filter selected with parameter visible. |
| assets/FLT_03-keyword-applied.webp | `Jura` keyword live-applied, narrowing the track count. |
| assets/FLT_03-keyword-reset-attempt.webp | Keyword cleared and preview restored to all tracks. |

## Timings

| Step | Timing |
|---|---:|
| Keyword filter parameter test | <3m |

## Handoff Notes

- Completed: FLT_03 terminal FAIL due missing explicit Apply/Cancel controls.
- Remaining unfinished coverage: continue with FLT_04.
- Blocked or not applicable: none.
- State left for the next packet: Activities by keyword selected with empty keyword and filtering enabled.
