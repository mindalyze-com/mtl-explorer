# Packet: TBS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_03
- In scope: sort presets/columns and visible summary totals.
- Out of scope: row opening, covered by TBS_05.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: Stats Tracks tab open with 10 tracks and search clear.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click sort controls and use search briefly.
- Not allowed: edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_03 | Clicked Date, Imported, Distance, Duration, Name, and Exploration sort controls; then searched `Walking` to check visible summary. | Sorting by each column/control works; summary row reflects what is visible. | Each sort control produced a distinct expected first row for the selected sort. Summary stayed at 10 tracks for all-track sorts and changed to `1 of 10 tracks` for the Walking subset. | PASS | `assets/TBS_03-sort-results.txt`, `assets/TBS_03-walking-summary.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_03-sort-results.txt | Sort matrix with first-row evidence and subset summary. |
| assets/TBS_03-walking-summary.webp | Track browser summary after Walking search. |

## Timings

| Step | Timing |
|---|---:|
| Sort and summary check | <4m |

## Handoff Notes

- Completed: TBS_03 terminal PASS.
- Remaining unfinished coverage: continue with TBS_04.
- Blocked or not applicable: none.
- State left for the next packet: Tracks tab open, search cleared.
