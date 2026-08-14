# Packet: TBS_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_09.
- In scope: monthly, weekly, and daily Statistics Trends charts.
- Out of scope: clicking Statistics entries, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_08.
- Required app/data state: all twelve tracks active.
- Required browser context: Statistics Trends in Charts view.

## Allowed Mutations

- Allowed: change aggregation and toggle Table/Charts.
- Not allowed: alter data or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_09 | Switched Trends among monthly, weekly, and daily aggregation; toggled Table and Charts. | Time-period charts render and switch correctly for all three resolutions. | All resolutions retained correct totals and rendered seven chart sections with the expected period labels. Table/Charts round-trip passed. | PASS | [periods](../assets/TBS_09-period-charts.txt), [daily charts](../assets/TBS_09-daily-charts.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_09-period-charts.txt](../assets/TBS_09-period-charts.txt) | Exact period labels and chart sections for every grouping. |
| [assets/TBS_09-daily-charts.webp](../assets/TBS_09-daily-charts.webp) | Daily chart view after the table round-trip. |

## Screenshot Evidence

The compact WebP shows the daily chart layout and resolved periods.

## Timings

| Step | Timing |
|---|---:|
| Grouping switch | < 1 s each |
| Table/Charts switch | < 1 s each |

## Handoff Notes

- Completed: TBS_09 is terminal `PASS`.
- Remaining unfinished coverage: TBS_10 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Trends open in daily Charts view; all twelve tracks.
