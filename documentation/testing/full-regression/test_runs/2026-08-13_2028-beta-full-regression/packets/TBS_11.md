# Packet: TBS_11

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_11.
- In scope: highlight drilldown list, track opening, excluded count, and excluded-track navigation.
- Out of scope: general filter/statistics set agreement, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_10.
- Required app/data state: all twelve tracks included in highlights.
- Required browser context: Statistics Overview.

## Allowed Mutations

- Allowed: open drilldown, temporarily exclude Moselradweg, inspect count/list, then restore Included.
- Not allowed: leave curation changed.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_11 | Opened Longest track drilldown and rank 1 details; temporarily excluded rank 1, clicked the excluded count, reloaded, and restored inclusion. | Ranked list and selected track open; excluded count exposes the excluded track list immediately. | Ranked list and #100002 details passed. Count showed 1, but its Excluded view was empty until a normal reload; reload then showed the correct row. Cleanup passed. | FAIL | [flow](../assets/TBS_11-highlight-drilldown.txt), [count](../assets/TBS_11-excluded-count.webp), [stale list](../assets/TBS_11-excluded-view-stale.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TBS-11-P2 | P2 | The excluded-highlight count opens an empty cached Excluded view until browser reload. | Exclude a drilldown track; wait for Overview to show 1 track excluded; click the count. | Statistics Tracks Excluded immediately lists that track. | The note shows 1 but opens 0 tracks. Normal reload recovers the expected Moselradweg row. | [flow](../assets/TBS_11-highlight-drilldown.txt), [stale list](../assets/TBS_11-excluded-view-stale.webp) | Users cannot inspect a newly excluded highlight from the provided count link without reloading. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_11-highlight-drilldown.txt](../assets/TBS_11-highlight-drilldown.txt) | Ranked drilldown, navigation, defect, recovery, and cleanup. |
| [assets/TBS_11-excluded-count.webp](../assets/TBS_11-excluded-count.webp) | Overview showing one excluded track. |
| [assets/TBS_11-excluded-view-stale.webp](../assets/TBS_11-excluded-view-stale.webp) | Empty Excluded view immediately after clicking the count. |

## Screenshot Evidence

The screenshots pair the correct count with the incorrect immediate destination.

## Timings

| Step | Timing |
|---|---:|
| Drilldown/detail open | < 1 s each |
| Exclusion save | < 1 s |
| Normal reload recovery | < 2 s |

## Handoff Notes

- Completed: TBS_11 is terminal `FAIL` with TBS-11-P2.
- Remaining unfinished coverage: TBS_12 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: cleanup verified; Statistics Overview open; all twelve tracks included.
