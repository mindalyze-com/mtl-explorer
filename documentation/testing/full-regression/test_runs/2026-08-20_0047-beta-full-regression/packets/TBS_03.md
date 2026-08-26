# Packet: TBS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_03
- In scope: Every exposed sort action and visible-set summary updates.
- Out of scope: Quick views, covered by TBS_04.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_02.
- Required app/data state: Eight-row filtered track browser.
- Required browser context: Filter Review desktop table.

## Allowed Mutations

- Allowed: Change sort order and search query.
- Not allowed: Change global filter or tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_03 | Activate each sort and compare full/one-row summaries. | Every sort works; summary follows visible rows. | Date, Imported, Distance, Duration, Name, and Exploration each reordered correctly; Walking search summary reduced to its one visible row. | PASS | [assets/TBS_03-sorting-summary.txt](../assets/TBS_03-sorting-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_03-sorting-summary.txt](../assets/TBS_03-sorting-summary.txt) | Sort order and full/filtered summary evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible rows and summaries are linked above.

## Timings

| Step | Timing |
|---|---:|
| Six sorts and visible-summary comparison | 5 min |

## Handoff Notes

- Completed: Sort actions and summary synchronization.
- Remaining unfinished coverage: None for TBS_03.
- Blocked or not applicable: None.
- State left for the next packet: Review tracks, empty search, eight rows, Exploration sort active.
