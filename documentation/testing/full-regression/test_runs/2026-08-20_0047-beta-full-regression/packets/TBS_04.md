# Packet: TBS_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_04
- In scope: Quick-view/preset switching with search and sorting usability.
- Out of scope: Row navigation, covered by TBS_05.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_03.
- Required app/data state: Stats Tracks with eight active rows and no excluded/no-activity rows in the current result.
- Required browser context: Statistics track browser.

## Allowed Mutations

- Allowed: Change quick view, search, and sort.
- Not allowed: Change track curation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_04 | Switch All/Excluded/Stats excluded/No activity with a search active, then sort after returning. | Presets switch correctly; search/sort remain usable. | Empty presets showed zero; All restored four searched rows; query persisted and Distance sort worked. | PASS | [assets/TBS_04-quick-views.txt](../assets/TBS_04-quick-views.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_04-quick-views.txt](../assets/TBS_04-quick-views.txt) | Preset states and preserved search/sort behavior. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible pressed and row states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Preset/search/sort matrix | 4 min |

## Handoff Notes

- Completed: Track-browser quick views and control usability.
- Remaining unfinished coverage: None for TBS_04.
- Blocked or not applicable: None.
- State left for the next packet: Stats Tracks, All preset, empty search, Distance sort active.
