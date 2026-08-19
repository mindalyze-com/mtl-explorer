# Packet: MED_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_15
- In scope: Camera-time-only interpolation, labeling, persisted route projection, and EXIF separation.
- Out of scope: Preview/saved camera correction.

## Prerequisites

- Required previous coverage IDs or run packets: MED_14.
- Required app/data state: Camera-time-only synthetic item 400000.
- Required browser context: Activity 100016 Photos tab and mini-map.

## Allowed Mutations

- Allowed: Read-only database inspection.
- Not allowed: Media edits.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_15 | Compared Estimated UI label/marker with original, route, correlation, and resolved persistence columns. | Persisted TRACK_INTERPOLATED result lies on route without creating original EXIF coordinates. | Item 400000 has NULL original EXIF coordinates; route and resolved coordinates are identical at 46.9483250001,7.4478500000 with TRACK_INTERPOLATED origin. | PASS | [assets/MED_14-15-position-persistence.txt](../assets/MED_14-15-position-persistence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_14-15-position-persistence.txt](../assets/MED_14-15-position-persistence.txt) | Exact original/route/resolved separation and UI provenance. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible label/marker descriptions and exact read-only coordinates provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| UI and database cross-check | About 3 s |

## Handoff Notes

- Completed: Track interpolation provenance passed.
- Remaining unfinished coverage: None for MED_15.
- Blocked or not applicable: None.
- State left for the next packet: No mutations; Photos remains open.
