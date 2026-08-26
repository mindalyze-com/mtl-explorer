# Packet: MED_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_15
- In scope: Camera-time-only Estimated label, on-route marker, persisted interpolation, separate resolved projection, and absence of fabricated EXIF coordinates.
- Out of scope: Previewing and saving camera correction, covered by MED_16 and MED_17.

## Prerequisites

- Required previous coverage IDs or run packets: MED_13 and DAT_08.
- Required app/data state: Original fixture 400002 correlated to track 100013.
- Required browser context: Track Details > Media, activity mini-map, and media viewer.

## Allowed Mutations

- Allowed: Select the fixture and run read-only spatial/database queries.
- Not allowed: Add EXIF coordinates or modify correction/location rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_15 | Select the camera-time-only photo, inspect its timeline/marker/viewer provenance, and compare EXIF, correlation, resolved projection, and raw route geometry. | UI says Estimated; marker is on the activity route; TRACK_INTERPOLATED persists separately without original EXIF coordinates. | UI and accessibility say Estimated. EXIF coordinate fields remain null. Correlation 3 and the resolved projection share the interpolated point/origin, less than 0.0001 m from the raw route. | PASS | [assets/MED_15-interpolated-provenance.txt](../assets/MED_15-interpolated-provenance.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_15-interpolated-provenance.txt](../assets/MED_15-interpolated-provenance.txt) | UI provenance and read-only EXIF/correlation/resolved/route measurements. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact accessible labels and spatial/database measurements are linked above.

## Timings

| Step | Timing |
|---|---:|
| UI and spatial provenance check | 3 min |

## Handoff Notes

- Completed: Estimated UI, route alignment, persisted correlation, separate resolved projection, and null original EXIF coordinates.
- Remaining unfinished coverage: None for MED_15.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Viewer is open on mtl-regression-estimated-a.jpg; no data was changed.
