# Packet: TRD_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_12.
- In scope: statistics exclusion and reinclusion with overview totals.
- Out of scope: highlight exclusion.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_11.
- Required app/data state: #100005 included in a 12-track statistics baseline.
- Required browser context: Track Details Quality and Statistics Overview.

## Allowed Mutations

- Allowed: exclude #100005 with reason Other, then restore Included.
- Not allowed: leave the record excluded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_12 | Changed #100005 Statistics from Included to Other, read Overview, then restored Included and read Overview again. | Excluded track leaves stats; reincluding restores it. | Both PATCH requests returned 200. Overview changed 12→11 tracks with the exact #100005 distance/time/energy/ascent removed, then returned to the 12-track baseline after reinclude. | PASS | [statistics exclusion](../assets/TRD_12-statistics-exclusion.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_12-statistics-exclusion.txt](../assets/TRD_12-statistics-exclusion.txt) | Exact baseline, excluded, restored totals and PATCH results. |

## Screenshot Evidence

Exact aggregate values and the restored state are recorded in text; no screenshot is needed for this reversible calculation check.

## Timings

| Step | Timing |
|---|---:|
| Exclude PATCH | 13 ms server time |
| Reinclude PATCH | 5 ms server time |

## Handoff Notes

- Completed: TRD_12.
- Remaining unfinished coverage: TRD_13 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Overview open with #100005 included and all 12 tracks counted.

