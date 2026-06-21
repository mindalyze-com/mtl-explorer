# Packet: TRD_12

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_12
- In scope: Verify Exclude from statistics removes a track from stats overview/totals and re-including restores it.
- Out of scope: Activity type changes, covered by TRD_10.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_11.
- Required app/data state: Track #100005 included in statistics before the test.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Temporarily exclude #100005 and restore inclusion afterward.
- Not allowed: Leave the track excluded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_12 | Changed Track #100005 statistics select from Included to `Exclude: GPS noise`, read grouped stats totals, then changed it back to Included and re-read totals. | Excluding stops the track counting in stats; re-including brings it back. | Stats total count dropped 13→12 and distance/energy dropped after exclusion; re-including cleared the reason and restored count 12→13 plus original distance/energy totals. | PASS | [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) | UI select states and API stats totals before/excluded/final. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct UI state and API totals are recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Exclude/re-include statistics check | ~4 min |

## Handoff Notes

- Completed: TRD_12.
- Remaining unfinished coverage: TRD_13 onward.
- Blocked or not applicable: none.
- State left for the next packet: Track #100005 is included in statistics again; `statisticsExclusionReason` is null.
