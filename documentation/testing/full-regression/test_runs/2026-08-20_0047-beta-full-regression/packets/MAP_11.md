# Packet: MAP_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_11
- In scope: Click a rendered high-zoom track point and verify popup metrics.
- Out of scope: Track details overview metrics.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_10 and DAT_08.
- Required app/data state: Six-point media track #100013 retained; point/direction layer enabled.
- Required browser context: Bern main map at high zoom.

## Allowed Mutations

- Allowed: Temporarily isolate Walking tracks and hide media markers; restore both after the check.
- Not allowed: Change server track/media data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_11 | At 30 m scale, click point 3 of isolated track #100013. | Point popup shows time, speed, elevation, and related metrics. | `Track #100013` popup showed Point 3/6, timestamp, coordinates, altitude, Speed row, distance, duration, ascent/descent, and energy metrics, with Close popup. | PASS | [assets/MAP_11-point-popup.txt](../assets/MAP_11-point-popup.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_11-point-popup.txt](../assets/MAP_11-point-popup.txt) | Isolation, exact point metrics, close control, and restored settings. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the complete accessible popup table is recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Point click to populated popup | 0.7 s observation wait |

## Handoff Notes

- Completed: High-zoom point popup and metric-table verification.
- Remaining unfinished coverage: None for MAP_11.
- Blocked or not applicable: None.
- State left for the next packet: Smart Base all-category result and media/track/point layers restored; map remains high-zoom in Bern.
