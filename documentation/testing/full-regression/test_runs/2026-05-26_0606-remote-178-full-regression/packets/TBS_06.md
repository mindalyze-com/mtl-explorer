# Packet: TBS_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_06
- In scope: statistics overview totals, activity breakdown, rankings/highlights, milestones, and period summaries.
- Out of scope: switching dedicated period chart tabs, covered by TBS_09.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: filtering off; 10 tracks visible.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open and scroll Stats Overview.
- Not allowed: change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_06 | Opened Stats Overview and reviewed the top and lower overview sections. | Statistics overview shows total distance, time, elevation/energy-related totals, activity breakdown, rankings, milestones, and period summaries/charts. | Overview showed 10 tracks, 1,262 km, 1d 03h duration, 7,052 Wh, Bicycle/Walking breakdown, highlight rankings, recent activity, most active day/week/month/weekday, milestones, and overall date range. | PASS | `assets/TBS_06-overview-top.webp`, `assets/TBS_06-overview-lower.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_06-overview-top.webp | Stats Overview totals, breakdown, and highlights. |
| assets/TBS_06-overview-lower.webp | Stats Overview lower-period and milestone area. |

## Timings

| Step | Timing |
|---|---:|
| Stats overview review | <3m |

## Handoff Notes

- Completed: TBS_06 terminal PASS.
- Remaining unfinished coverage: continue with TBS_07.
- Blocked or not applicable: none.
- State left for the next packet: Stats Overview open.
