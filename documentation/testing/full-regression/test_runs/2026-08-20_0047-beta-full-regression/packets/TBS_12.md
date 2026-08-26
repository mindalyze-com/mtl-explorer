# Packet: TBS_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_12
- In scope: Geo-drawn filter parity across map, Overview, Trends, and Stats Tracks before/after reload.
- Out of scope: Statistics summary navigation, covered by TBS_13.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_11.
- Required app/data state: Seven-track Smart Base baseline and remaining Lannion track.
- Required browser context: Map, Filter criteria, and all Statistics tabs.

## Allowed Mutations

- Allowed: Draw one circle and reload.
- Not allowed: Delete or edit tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_12 | Draw a Lannion circle, compare four surfaces, reload, and compare again after resolution. | Every surface uses the same resolved track set before and after fallback. | All settled surfaces matched one Lannion track and 25.9 km both times. | PASS | [assets/TBS_12-geo-stats-parity.txt](../assets/TBS_12-geo-stats-parity.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_12-geo-stats-parity.txt](../assets/TBS_12-geo-stats-parity.txt) | Geo setup and pre/post-reload cross-view values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible counts, row, and totals are linked above.

## Timings

| Step | Timing |
|---|---:|
| Circle draw and first parity check | 5 min |
| Reload, resolution wait, and second parity check | 4 min |

## Handoff Notes

- Completed: Geo-filter Statistics parity before and after reload.
- Remaining unfinished coverage: None for TBS_12.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview with Showing 1 of 7 tracks and active Lannion circle.
