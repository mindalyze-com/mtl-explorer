# Packet: TRD_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_06
- In scope: Bidirectional chart/mini-map hover synchronization and cursor cleanup.
- Out of scope: Point-popup clicking covered by MAP_11.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_05 and DAT_07.
- Required app/data state: Populated GPX graphs and frozen four-point Segment B geometry.
- Required browser context: Graphs and selected-track mini-map.

## Allowed Mutations

- Allowed: Move the pointer over known rendered surfaces/vertices.
- Not allowed: Guess unknown map locations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_06 | Hovered GPX Speed chart and inspected its mini-map marker, cleared it, then hovered a derived exact Segment B vertex and inspected chart crosshair/tooltip before clearing again. | Either surface highlights the matching point in the other; no stale cursors remain. | Chart hover created a visible mini-map marker plus crosshair/tooltip. Mini-map vertex hover created a visible chart crosshair/tooltip. Leaving each surface hid tooltip/crosshair and removed marker. | PASS | [assets/TRD_06-hover-link.txt](../assets/TRD_06-hover-link.txt), [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_06-hover-link.txt](../assets/TRD_06-hover-link.txt) | Bidirectional visible cursor/tooltip/marker and cleanup measurements. |
| [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) | Exact synthetic vertex provenance. |

## Screenshot Evidence

Unavailable under ACC_04. DOM-backed Highcharts crosshair/tooltip and MapLibre marker measurements provided direct hover evidence.

## Timings

| Step | Timing |
|---|---:|
| GPX chart hover and clear | About 1 s |
| Synthetic mini-map hover and clear | About 1 s |

## Handoff Notes

- Completed: Both hover directions and no-stale-cursor checks.
- Remaining unfinished coverage: None for TRD_06.
- Blocked or not applicable: None.
- State left for the next packet: Track 100017 Graphs open; hover cursors cleared.
