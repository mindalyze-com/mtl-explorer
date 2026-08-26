# Packet: MED_18

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_18
- In scope: Manual location/note UI, Set by you provenance, separate evidence storage, precedence, and fallback after clear.
- Out of scope: Overlapping-activity ambiguity, covered by MED_19.

## Prerequisites

- Required previous coverage IDs or run packets: MED_14, MED_15, and MED_17 cleanup.
- Required app/data state: Eight-item baseline; no corrections or manual locations.
- Required browser context: Track 100013 Media tab, Media tools, activity mini-map, and viewer.

## Allowed Mutations

- Allowed: Set/edit/clear disposable manual locations and notes on one EXIF and one interpolated fixture.
- Not allowed: Modify original EXIF or track-correlation evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_18 | Set and view manual locations/notes on photo-a and estimated-a; inspect all evidence rows; clear both assignments. | Item/marker say Set by you; manual/EXIF/track evidence stays separate; resolution is USER_ASSIGNED > EXIF_EMBEDDED > TRACK_INTERPOLATED; clear falls back. | Both timeline, activity marker, viewer, and location map said Set by you. SQL retained separate manual, EXIF, and route points. Clear restored Photo GPS for 400003 and Estimated for 400002, with lower-priority evidence unchanged. | PASS | [assets/MED_18-manual-precedence.txt](../assets/MED_18-manual-precedence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_18-manual-precedence.txt](../assets/MED_18-manual-precedence.txt) | UI/accessibility state and before/after read-only evidence for both precedence branches. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact labels, coordinates, notes, and database evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| EXIF manual assignment and clear | 4 min |
| Interpolated manual assignment and clear | 4 min |

## Handoff Notes

- Completed: Manual location/note, Set by you UI, evidence separation, precedence, and both fallback branches.
- Remaining unfinished coverage: None for MED_18.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: No manual rows or time corrections; original eight-item baseline restored; location edit mode remains open.
