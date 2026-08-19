# Packet: MED_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_14
- In scope: Embedded-GPS provenance and original EXIF coordinate preservation.
- Out of scope: Camera-time-only interpolation.

## Prerequisites

- Required previous coverage IDs or run packets: MED_13.
- Required app/data state: Embedded-GPS synthetic items 400005 and 400002.
- Required browser context: Activity 100016 Photos tab and mini-map.

## Allowed Mutations

- Allowed: Read-only database inspection.
- Not allowed: Media edits.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_14 | Compared Photo GPS UI labels and markers with original/resolved persistence columns after restart. | Embedded-GPS item remains EXIF_EMBEDDED with unchanged original EXIF point. | Photo GPS is visible; original 46.94800000,7.44740000 equals the resolved EXIF_EMBEDDED point for item 400005. Additional GPS rows agree. | PASS | [assets/MED_14-15-position-persistence.txt](../assets/MED_14-15-position-persistence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_14-15-position-persistence.txt](../assets/MED_14-15-position-persistence.txt) | UI provenance and read-only original/resolved coordinates. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible UI labels and exact read-only coordinate columns provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| UI and database cross-check | About 3 s |

## Handoff Notes

- Completed: Embedded-GPS provenance passed.
- Remaining unfinished coverage: None for MED_14.
- Blocked or not applicable: None.
- State left for the next packet: No mutations; Photos remains open.
