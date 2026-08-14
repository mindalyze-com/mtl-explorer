# Packet: TBS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_03.
- In scope: every exposed Track Browser sort and visible-result summary.
- Out of scope: quick views, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_02.
- Required app/data state: all twelve tracks available.
- Required browser context: Statistics Tracks desktop table.

## Allowed Mutations

- Allowed: change sort chips and run one summary search.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_03 | Activated Date, Imported, Distance, Duration, Name, and Exploration sorts; then searched Walking. | Each column sort works and summary reflects currently visible rows. | Every chip became active and produced the appropriate first row. Full totals stayed 12/993 km/21h 43m; Walking recalculated to 1/3.60 km/59m 57s. | PASS | [sorts](../assets/TBS_03-sorts.txt), [visible summary](../assets/TBS_03-visible-summary.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_03-sorts.txt](../assets/TBS_03-sorts.txt) | Active sort, first row, and summary for every option. |
| [assets/TBS_03-visible-summary.webp](../assets/TBS_03-visible-summary.webp) | Filtered summary after the final sort. |

## Screenshot Evidence

The WebP shows the one-row visible summary after sorting and searching.

## Timings

| Step | Timing |
|---|---:|
| Each sort | < 1 s |
| Summary recalculation | < 1 s |

## Handoff Notes

- Completed: TBS_03 is terminal `PASS`.
- Remaining unfinished coverage: TBS_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Tracks open; filter paused; Walking search active; Exploration sort active.
