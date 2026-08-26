# Packet: MED_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_14
- In scope: Embedded-GPS photo label, immutable original EXIF point, and resolved provenance.
- Out of scope: Camera-time-only interpolation, covered by MED_15.

## Prerequisites

- Required previous coverage IDs or run packets: MED_13 and DAT_08.
- Required app/data state: Original media fixture 400003 correlated to track 100013.
- Required browser context: Track Details > Media and the activity media viewer.

## Allowed Mutations

- Allowed: Open the fixture in the viewer and run read-only database queries.
- Not allowed: Edit media metadata, correction, or location rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_14 | Open embedded-GPS photo 400003 and compare its UI provenance, original EXIF columns, resolved projection, and frozen manifest point. | UI says Photo GPS; original EXIF point is unchanged; resolved origin is EXIF_EMBEDDED. | Timeline and viewer say Photo GPS; viewer shows Embedded GPS time and the exact manifest coordinates. Database EXIF scalar/geometry and resolved geometry are identical, with origin EXIF_EMBEDDED and no correlation ID. | PASS | [assets/MED_14-exif-provenance.txt](../assets/MED_14-exif-provenance.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_14-exif-provenance.txt](../assets/MED_14-exif-provenance.txt) | Viewer state and read-only EXIF/resolved-position row. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Frozen synthetic fixture point and expected origin. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact viewer text and database coordinates are linked above.

## Timings

| Step | Timing |
|---|---:|
| Viewer and database provenance check | 2 min |

## Handoff Notes

- Completed: Photo GPS label and original/resolved EXIF position separation.
- Remaining unfinished coverage: None for MED_14.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Viewer is open on mtl-regression-photo-a.jpg; the activity timeline remains unchanged.
