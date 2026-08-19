# Packet: MED_18

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_18
- In scope: Manual location/note, Set by you UI, persistence separation, precedence, and clear fallback.
- Out of scope: Camera correction preview under a manual assignment, covered by MED_22.

## Prerequisites

- Required previous coverage IDs or run packets: MED_15 and MED_17.
- Required app/data state: Estimated item 400000 with preserved selected route projection.
- Required browser context: Activity Photos with Photo tools.

## Allowed Mutations

- Allowed: Save and clear one disposable manual location and note.
- Not allowed: Direct database writes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_18 | Saved 46.9484,7.4479 plus a note on estimated item 400000, inspected separation/precedence, then cleared it. | UI uses Set by you; manual/EXIF/track data remain separate; USER_ASSIGNED wins and clearing restores prior origin. | Set by you and USER_ASSIGNED appeared; track route and NULL EXIF stayed intact; clear removed manual row and restored exact TRACK_INTERPOLATED location. | PASS | [assets/MED_18-manual-location.txt](../assets/MED_18-manual-location.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_18-manual-location.txt](../assets/MED_18-manual-location.txt) | Exact end-user flow and before/during/after persistence values. |

## Screenshot Evidence

Live screenshots confirmed the expanded location editor and Set by you state; exact accessible UI and persistence values are recorded in the evidence file.

## Timings

| Step | Timing |
|---|---:|
| Save and refresh | About 1.4 s |
| Clear and refresh | About 1.3 s |

## Handoff Notes

- Completed: Manual precedence and fallback passed.
- Remaining unfinished coverage: None for MED_18.
- Blocked or not applicable: None.
- State left for the next packet: Manual assignment cleared; Estimated baseline restored.
