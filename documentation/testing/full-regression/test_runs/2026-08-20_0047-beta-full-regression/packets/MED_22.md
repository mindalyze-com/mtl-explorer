# Packet: MED_22

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_22
- In scope: Manual-location precedence in the card, activity mini-map, viewer, and viewer location map during a non-zero camera-clock preview, including the clear action.
- Out of scope: Saving the clock correction, covered by MED_23.

## Prerequisites

- Required previous coverage IDs or run packets: MED_18 precedence behavior and MED_21 cleanup.
- Required app/data state: Eight-item baseline with no manual locations or time corrections.
- Required browser context: Track 100013 Media tab, expanded Media tools, activity mini-map, and media viewer.

## Allowed Mutations

- Allowed: Assign and clear one manual location and run an unsaved +0.25-hour preview.
- Not allowed: Save the preview or change original EXIF/correlation evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_22 | Assign a manual point to estimated-a, preview +15 minutes, inspect card/markers/viewer/editor, then clear the point during the preview. | Manual `Set by you` position and clear action remain while preview route and time update underneath. | Card and both location maps retained Set by you at 46.94855,7.44825; Clear assignment remained visible. Preview changed time 10:01 -> 10:16 and route distance 44.83 -> 131.80 m without changing persisted evidence. Clear restored the exact baseline. | PASS | [assets/MED_22-manual-offset-preview.txt](../assets/MED_22-manual-offset-preview.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_22-manual-offset-preview.txt](../assets/MED_22-manual-offset-preview.txt) | Accessible UI labels, preview values, read-only evidence separation, and cleanup counts. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact accessible names, values, and database evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| Manual assignment and preview | 4 min |
| Viewer/mini-map/clear verification and cleanup | 3 min |

## Handoff Notes

- Completed: Manual assignment, non-zero preview, card/viewer/mini-map precedence, clear-action availability, evidence separation, and cleanup.
- Remaining unfinished coverage: None for MED_22.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Exact eight-item baseline; no manual rows, time corrections, or work rows; Media tools open and location adjustment mode off.
