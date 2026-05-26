# Packet: LOC_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: LOC_03
- In scope: Locale preference persistence across reload.
- Out of scope: Cross-surface formatting completeness, covered by LOC_02.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_02
- Required app/data state: de-DE format locale selected.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Hard reload and reopen Settings.
- Not allowed: Clear local preferences.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_03 | Hard reloaded with de-DE selected, reopened Admin -> Settings, and inspected the selected locale and preview. | Locale persists across reload. | After reload, Settings still showed de-DE selected and the preview stayed in de-DE format: `26.05.2026 ... 12.345,67`. | PASS | `assets/LOC_03-locale-persistence.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/LOC_03-locale-persistence.txt | Selected-locale and preview evidence after reload. |

## Timings

| Step | Timing |
|---|---:|
| Reload and reopen Settings | <2 min |

## Handoff Notes

- Completed: LOC_03 passed.
- Remaining unfinished coverage: LOC_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: de-DE persistence verified; locale restored to en-GB after LOC_04 evidence collection.
