# Packet: MAP_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_07
- In scope: Direction arrows on a valid real track at high zoom with the point/direction layer enabled.
- Out of scope: Sparse two-point or off-viewport synthetic evidence.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_06.
- Required app/data state: Real public track 100004 and Track points and direction layer.
- Required browser context: Signed-in root map.

## Allowed Mutations

- Allowed: Inspect map settings, search a location, select a track, and zoom.
- Not allowed: Guess canvas coordinates or substitute invalid sparse-track evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_07 | Confirmed Track points and direction enabled at 100%, centered on Lannion at 100 m scale, and selected multi-point public track 100004 while preserving that scale. | Direction arrows appear on visible in-viewport vertices at high zoom. | All prerequisites were established with a valid real track. Arrows/vertices are canvas-only, lack semantic targets, and cannot be seen because ACC_04 blocks screenshots. | BLOCKED | [assets/MAP_07-direction.txt](../assets/MAP_07-direction.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_07-direction.txt](../assets/MAP_07-direction.txt) | Enabled setting, valid real-track/high-zoom preparation, and visual constraint. |

## Screenshot Evidence

Required to inspect canvas arrows/vertices but unavailable under ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Inspect Your data layer settings | About 4 s |
| Search Lannion and settle at 100 m | About 4 s |
| Select the public track | About 8 s |

## Handoff Notes

- Completed: Enabled layer check and valid real-track high-zoom setup.
- Remaining unfinished coverage: None; terminally blocked for direct arrow visibility.
- Blocked or not applicable: ACC_04 plus canvas-only point/arrow rendering.
- State left for the next packet: Track 100004 open over Lannion at 100 m scale.
