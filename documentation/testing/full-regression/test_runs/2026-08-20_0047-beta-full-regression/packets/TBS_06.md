# Packet: TBS_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_06
- In scope: Statistics Overview totals, elevation, activity breakdown, rankings, milestones, and active-period displays.
- Out of scope: Dataset-size comparisons, covered by TBS_07.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_05.
- Required app/data state: Eight-track active year result.
- Required browser context: Statistics Overview.

## Allowed Mutations

- Allowed: Navigate to Overview.
- Not allowed: Change data or filter during inspection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_06 | Inspect every required Overview section. | Totals and all derived sections are populated. | Totals, ascent, breakdown, highlights, active periods, milestones, and recent activity were populated and consistent. | PASS | [assets/TBS_06-overview-content.txt](../assets/TBS_06-overview-content.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_06-overview-content.txt](../assets/TBS_06-overview-content.txt) | Exact populated Overview sections and values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible Overview structure is linked above.

## Timings

| Step | Timing |
|---|---:|
| Full Overview inspection | 3 min |

## Handoff Notes

- Completed: Required Statistics Overview content.
- Remaining unfinished coverage: None for TBS_06.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview with eight filtered tracks.
