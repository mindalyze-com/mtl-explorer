# Packet: TBS_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_05
- In scope: opening track details from a browser row.
- Out of scope: detail tab behavior, covered by TRD.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: Stats Tracks tab open with visible rows.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click a track row.
- Not allowed: edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_05 | Clicked the Track #100005 row in the Tracks table. | Clicking a row opens the track's details. | The detail sheet opened for `#100005`, showing the FIT-backed overview with Activity.fit, distance, duration, ascent, and download buttons. | PASS | `assets/TBS_05-row-opens-detail.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_05-row-opens-detail.webp | Track row click opened #100005 details. |

## Timings

| Step | Timing |
|---|---:|
| Row click open detail | <2m |

## Handoff Notes

- Completed: TBS_05 terminal PASS.
- Remaining unfinished coverage: continue with TBS_06.
- Blocked or not applicable: none.
- State left for the next packet: track #100005 details open over Stats Tracks tab.
