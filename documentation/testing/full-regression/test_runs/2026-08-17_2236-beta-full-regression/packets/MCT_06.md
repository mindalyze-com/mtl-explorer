# Packet: MCT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_06
- In scope: Segment comparison geometry remains within real local bounds.
- Out of scope: Basic chart rendering and extraction inclusiveness.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_05.
- Required app/data state: Five selected Bern-area comparison tracks.
- Required browser context: A-B Compare open.

## Allowed Mutations

- Allowed: Inspect comparison map state and read canonical track geometry.
- Not allowed: Modify tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_06 | Audited the five selected canonical geometries and the rendered comparison mini-map scale. | Lines stay within real local bounds with no global line, `[0,0]`, or off-continent jump. | All 22 points stayed in lng 7.4468..7.4490 / lat 46.9475..46.9492 with sub-0.001° steps and no zero/off-continent point; the map retained a local 100 m scale. | PASS | [assets/MCT_06-geometry-sanity.txt](../assets/MCT_06-geometry-sanity.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_06-geometry-sanity.txt](../assets/MCT_06-geometry-sanity.txt) | Per-track/combined bounds, max steps, and map scale assertions. |

## Screenshot Evidence

Unavailable under ACC_04. Exact live geometry and accessible map scale provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Geometry and map-scale audit | About 2 s |

## Handoff Notes

- Completed: Segment geometry sanity.
- Remaining unfinished coverage: None for MCT_06.
- Blocked or not applicable: None.
- State left for the next packet: Compare remains open for virtual-race coverage.

