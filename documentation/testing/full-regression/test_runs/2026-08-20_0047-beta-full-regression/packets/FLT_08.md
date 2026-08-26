# Packet: FLT_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_08
- In scope: Clear active filter and restore all tracks.
- Out of scope: Category subset persistence.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_03-FLT_07.
- Required app/data state: Keyword/date criteria narrow the result to one track.
- Required browser context: Authenticated Filter and Stats.

## Allowed Mutations

- Allowed: Use Reset filter.
- Not allowed: Leave the result narrowed.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_08 | Use Reset filter, then inspect map and Statistics. | All tracks are restored. | Smart Base/No criteria returned; map and Stats both returned to 9 tracks and full totals. | PASS | [assets/FLT_08-reset.txt](../assets/FLT_08-reset.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_08-reset.txt](../assets/FLT_08-reset.txt) | Before/after filter reset state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible result states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Reset and cross-view verification | 2 min |

## Handoff Notes

- Completed: Filter reset and all-track restoration.
- Remaining unfinished coverage: None for FLT_08.
- Blocked or not applicable: None.
- State left for the next packet: Smart Base Filter, no criteria, nine-track baseline.
