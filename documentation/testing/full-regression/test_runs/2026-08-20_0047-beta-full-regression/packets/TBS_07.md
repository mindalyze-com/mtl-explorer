# Packet: TBS_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_07
- In scope: Statistics correctness for empty, single-track, and many-track resolved sets.
- Out of scope: Required import/delete transition, covered by TBS_08.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01, TBS_06.
- Required app/data state: Same-run empty baseline and current nine-track dataset.
- Required browser context: Filter and Statistics Overview.

## Allowed Mutations

- Allowed: Apply one-track keyword filter and reset it.
- Not allowed: Delete tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_07 | Compare empty baseline, one Media track, and full Smart Base result. | Counts and totals match each set without stale values. | 0, 1, and 9-track states each exposed their expected totals and breakdown. | PASS | [assets/TBS_07-dataset-sizes.txt](../assets/TBS_07-dataset-sizes.txt); [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_07-dataset-sizes.txt](../assets/TBS_07-dataset-sizes.txt) | Single/many values and empty-baseline reference. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Same-run empty state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible totals are linked above.

## Timings

| Step | Timing |
|---|---:|
| Single filter and many reset comparison | 4 min |
| Empty baseline reuse | 1 min |

## Handoff Notes

- Completed: Statistics across 0/1/9-track sets.
- Remaining unfinished coverage: None for TBS_07.
- Blocked or not applicable: None.
- State left for the next packet: Smart Base Statistics Overview with nine tracks.
