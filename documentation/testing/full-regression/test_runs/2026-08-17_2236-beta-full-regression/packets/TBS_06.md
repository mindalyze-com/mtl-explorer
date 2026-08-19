# Packet: TBS_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_06
- In scope: Statistics Overview totals, activity breakdown, rankings, milestones, and active-period displays.
- Out of scope: Period chart switching covered by TBS_09.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_05.
- Required app/data state: Reset filter with the complete 15-track resolved set.
- Required browser context: Statistics Overview on desktop.

## Allowed Mutations

- Allowed: Open Overview and read its rendered sections.
- Not allowed: Change filters or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_06 | Read top totals, activity breakdown, all highlight rankings, recent activity, active day/week/month/weekday, milestones, and covered range. | Overview shows total distance/time/elevation, activity breakdown, rankings, milestones, and period displays. | Every required section rendered with coherent values for the 15-track set, including 1,048 km, 1d 00h, 14,963 m ascent, 12/2/1 activities, eight highlight cards, four active-period cards, and seven milestones. | PASS | [assets/TBS_06-overview.txt](../assets/TBS_06-overview.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_06-overview.txt](../assets/TBS_06-overview.txt) | Exact totals and representative values from every required Overview section. |

## Screenshot Evidence

Unavailable under ACC_04. Full accessible section/card inventory and exact values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Overview load and inventory | About 2 s |

## Handoff Notes

- Completed: Totals, breakdown, rankings, recent activity, active periods, milestones, and date range.
- Remaining unfinished coverage: None for TBS_06.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview remains open on the unfiltered 15-track set.
