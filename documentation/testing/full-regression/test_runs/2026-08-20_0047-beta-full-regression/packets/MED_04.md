# Packet: MED_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_04
- In scope: HEIC media indexes and displays through server-side conversion.
- Out of scope: Broken-image recovery.

## Prerequisites

- Required previous coverage IDs or run packets: MED_03.
- Required app/data state: No HEIC existed; one temporary HEIC derived from a public repository image.
- Required browser context: Admin Rescan Media, Trends Undated, and media viewer.

## Allowed Mutations

- Allowed: Add one disposable HEIC, rescan, view, and later remove it.
- Not allowed: Use private photos.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_04 | Generate/index a 96x96 HEIC, open its Undated mosaic item and full viewer. | Server converts HEIC and all thumbnail/full surfaces display correctly. | MEDIA indexed the HEIC; mosaic thumbnail rendered; viewer loaded a complete 96x96 blob image and cleared its loading status. | PASS | [assets/MED_04-heic-display.txt](../assets/MED_04-heic-display.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_04-heic-display.txt](../assets/MED_04-heic-display.txt) | Fixture, indexing, mosaic, and decoded-image evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; decoded natural dimensions and viewer state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Generate and transfer HEIC | 2 min |
| Rescan/correlate | <1 min |
| Mosaic/viewer validation | 2 min |

## Handoff Notes

- Completed: HEIC indexing and server-converted display.
- Remaining unfinished coverage: None for MED_04.
- Blocked or not applicable: None.
- State left for the next packet: Viewer open on temporary media ID 400008; HEIC remains in watched folder for broken-source recovery testing.
